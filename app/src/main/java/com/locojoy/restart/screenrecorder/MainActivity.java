package com.locojoy.restart.screenrecorder;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.MediaCodecInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StrictMode;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.os.Build.VERSION_CODES.M;
import static com.locojoy.restart.screenrecorder.ScreenRecorder.AUDIO_AAC;
import static com.locojoy.restart.screenrecorder.ScreenRecorder.VIDEO_AVC;

import com.unity3d.player.UnityPlayer;
import com.unity3d.player.UnityPlayerActivity;


public class MainActivity extends UnityPlayerActivity {
//    public class MainActivity extends Activity {
    private int DEFAULT_RECORDER_TIME = 180 * 1000; //默认270秒
    public static final String ACTION_STOP = "com.locojoy.restart.action.STOP";
    private static final int REQUEST_MEDIA_PROJECTION = 1;
    private static final int REQUEST_PERMISSIONS = 2;
    private static String screenRecorderPath; //录屏文档路径
    private MediaProjectionManager mMediaProjectionManager;
    private ScreenRecorder mRecorder;
    private Notifications mNotifications;
    private VideoEncodeConfig mVideoEncodeConfig; //设置视频配置
    private AudioEncodeConfig mAudioEncodeConfig;  //设置音频配置
    Window window;
    public static boolean useThemestatusBarColor = false;//是否使用特殊的标题栏背景颜色，android5.0以上可以设置状态栏背景色，如果不使用则使用透明色值
    public static boolean useStatusBarColor = true;//是否使用状态栏文字和图标为暗色，如果状态栏采用了白色系，则需要使状态栏和图标为暗色，android6.0以上可以设置
    private static final long COLLAPSE_SB_PERIOD = 100;
    private static final int COLLAPSE_STATUS_BAR = 1000;
    private static final String TAG = "ScreenRecorder";
    private String filePath;

    private final int RECORDING = 0; //正在录制
    private final int END = 1; //已经结束
    private final int ERROR = 2; //异常
    private final int NO_PERMISSIONS = 3; //权限不足
    private int state = -1; //当前录屏默认状态

    private int mWith = 0;
    private int mHeight = 0;
    private Thread threadTime;
    private boolean isHandStop = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.demo);
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window = this.getWindow();
        android.graphics.Point point = getSize(this);
        mWith = point.x;
        mHeight = point.y;
        init();
        state = -1; //还原
    }

    public static android.graphics.Point getSize(Activity act) {
        android.view.Display display = act.getWindowManager().getDefaultDisplay();
        android.graphics.Point size = new android.graphics.Point();
        display.getSize(size);  //size.x就是宽度，size.y就是高度
        return size;
    }

    @TargetApi(21)
    private void init() {
        mMediaProjectionManager = (MediaProjectionManager) getApplicationContext().getSystemService(MEDIA_PROJECTION_SERVICE);
        mNotifications = new Notifications(getApplicationContext());
    }

    /**
     * 检查当前手机是否支持系统录屏api 1
     * sdk_int>21 LOLLIPOP android 5.0 系统
     *
     * @return true or false
     */
    public boolean IsSupportScreenRecorder() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    /**
     * 开始录制 2
     */
    public void startRecorderAndCheckPermissions() {
        boolean hasPermissions = hasPermissions();
        if (hasPermissions) {
            startCaptureIntent();
        } else if (Build.VERSION.SDK_INT >= M) {
            requestPermissions();
        } else {
            toast("No permission to write sd card");
        }
    }

    /**
     * 录制时间
     *
     * @param recorderTimeMillis 毫秒
     */
    public void startRecorderAndCheckPermissions(int recorderTimeMillis) {
        if (recorderTimeMillis > 0) {
            DEFAULT_RECORDER_TIME = recorderTimeMillis;
            Logger("录制时间：" + recorderTimeMillis / 1000);
        }
        if (hasPermissions()) {
            startCaptureIntent();
        } else if (Build.VERSION.SDK_INT >= M) {
            requestPermissions();
        } else {
            toast("No permission to write sd card");
        }
    }

    /**
     * 获得返回路径
     *
     * @return
     */
    public String getScreenRecorderFilePath() {
        return screenRecorderPath == null ? "" : screenRecorderPath;
    }

    /**
     * 创建自定义VideoEncode
     *
     * @param width     屏幕宽
     * @param height    屏幕高
     * @param bitrate   比特率
     * @param framerate 帧率
     * @param iframe
     * @param codec     编码方式
     * @param videoAvc  视频Avc
     * @param level     水平
     */
    public void setVideoEncodeConfig(int width, int height, int bitrate, int framerate, int iframe, String codec, String videoAvc, String level) {
        mVideoEncodeConfig = createVideoConfig(width, height, bitrate, framerate, iframe, codec, videoAvc, level);
    }


    public void setAudioEncodeConfig(String codec, int bitrate, int samplerate, int channelCount, int profile) {
        mAudioEncodeConfig = new AudioEncodeConfig(codec, AUDIO_AAC, bitrate, samplerate, channelCount, profile);
    }


    /**
     * 自定义声音编码
     *
     * @param codec        编码方式
     * @param bitrate      比特率
     * @param samplerate   采样率
     * @param channelCount 信道计数
     * @param profile
     * @return
     */
    private AudioEncodeConfig createAudioConfig(String codec, int bitrate, int samplerate, int channelCount, int profile) {
        return new AudioEncodeConfig(codec, AUDIO_AAC, bitrate, samplerate, channelCount, profile);
    }


    private VideoEncodeConfig createVideoConfig(int width, int height, int bitrate, int framerate, int iframe, String codec, String videoAvc, String level) {
        MediaCodecInfo.CodecProfileLevel profileLevel = Utils.toProfileLevel(level);
        return new VideoEncodeConfig(width, height, bitrate,
                framerate, iframe, codec, videoAvc, profileLevel);
    }


    /**
     * 停止录制 3
     */
    public void stopRecorderAction() {
        if (mRecorder != null) {
            stopRecorder();
        }
    }

    /**
     * true 横屏
     *
     * @return
     */
    private boolean isLandScape() {
        Configuration config = getResources().getConfiguration();
// 如果是横屏则设为竖屏
        return config.orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            MediaProjection mediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
            if (mediaProjection == null) {
                toast("没有权限");
                UnityPlayer.UnitySendMessage("GameManagerForRecord", "StopRecord", "");
                Log.e("@@", "media projection is null");
                return;
            }
            VideoEncodeConfig video = mVideoEncodeConfig == null ? createVideoConfig() : mVideoEncodeConfig;
            AudioEncodeConfig audio = mAudioEncodeConfig == null ? createAudioConfig() : mAudioEncodeConfig; // audio can be null
            if (video == null) {
                toast("Create ScreenRecorder failure");
                mediaProjection.stop();
                return;
            }
            File dir = getSavingDir();
            if (!dir.exists() && !dir.mkdirs()) {
                cancelRecorder();
                return;
            }
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
            final File file = new File(dir, "Screen-" + format.format(new Date())
                    + "-" + video.width + "x" + video.height + ".mp4");
            Log.d("@@", "Create recorder with :" + video + " \n " + audio + "\n " + file);
            mRecorder = newRecorder(mediaProjection, video, audio, file);
            screenRecorderPath = file.getAbsolutePath();

            if (hasPermissions()) {

                startRecorder();
            } else {

                cancelRecorder();
            }
        }
    }

    private ScreenRecorder newRecorder(MediaProjection mediaProjection, VideoEncodeConfig video,
                                       AudioEncodeConfig audio, final File output) {
        ScreenRecorder r = new ScreenRecorder(video, audio, 1, mediaProjection, output.getAbsolutePath());
        r.setCallback(new ScreenRecorder.Callback() {
            long startTime = 0;

            @Override
            public void onStop(Throwable error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        stopRecorder();
                    }
                });
                if (error != null) {
                    toast("Recorder error ! See logcat for more details");
                    error.printStackTrace();
                    state = ERROR;
                    output.delete();
                } else {
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                            .addCategory(Intent.CATEGORY_DEFAULT)
                            .setData(Uri.fromFile(output));
                    sendBroadcast(intent);
                }
            }

            @Override
            public void onStart() {
                mNotifications.recording(0);
            }

            @Override
            public void onRecording(long presentationTimeUs) {
                if (startTime <= 0) {
                    startTime = presentationTimeUs;
                }
                long time = (presentationTimeUs - startTime) / 1000;
                mNotifications.recording(time);
            }
        });
        return r;
    }

    /**
     * AudioEncodeConfig{
     * codecName='OMX.google.aac.encoder',
     * mimeType='audio/mp4a-latm',
     * bitRate=80000, sampleRate=44100, channelCount=1, profile=1}
     *
     * @return
     */
    private AudioEncodeConfig createAudioConfig() {
        String codec = "OMX.google.aac.encoder";
        int bitrate = 80000;
        int samplerate = 44100;
        int channelCount = 1;
        int profile = 1;
        return new AudioEncodeConfig(codec, AUDIO_AAC, bitrate, samplerate, channelCount, profile);
    }


    /**
     * VideoEncodeConfig{\
     * width=720,
     * height=1280,
     * bitrate=800000,
     * framerate=30,
     * iframeInterval=1,
     * codecName='OMX.qcom.video.encoder.avc', //OMX.google.h264.encoder
     * mimeType='video/avc',
     * codecProfileLevel=}
     * video size
     * int[] selectedWithHeight = getSelectedWithHeight();
     */

    private VideoEncodeConfig createVideoConfig() {
//        boolean isLandscape = isLandScape();
        boolean isLandscape = true;
        final String codec = "OMX.google.h264.encoder";
        int width = isLandscape ? mHeight : mWith;
        int height = isLandscape ? mWith : mHeight;
//        int width = isLandscape ? 1280 : 720;
//        int height = isLandscape ? 720 : 1280;
        int framerate = 60;
        int iframe = 1;
        int bitrate = 6500;
        MediaCodecInfo.CodecProfileLevel profileLevel = null;
        return new VideoEncodeConfig(width, height, bitrate,
                framerate, iframe, codec, VIDEO_AVC, profileLevel);
    }

    private static File getSavingDir() {
        //是否挂载sd卡
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "ScreenCaptures");
        screenRecorderPath = file.getAbsolutePath();
        return file;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS) {
            int granted = PackageManager.PERMISSION_GRANTED;
            for (int r : grantResults) {
                granted |= r;
            }
            if (granted == PackageManager.PERMISSION_GRANTED) {
                startCaptureIntent();
                toast("成功!");
                //UnityPlayer.UnitySendMessage("GameManagerForRecord", "StartRecord", "");
            } else {
                state = NO_PERMISSIONS; //没有权限
                toast("No Permission!");
            }
        }
    }

//    @Override
//    public void onRequestPermissionsResult(int requestCode,  String[] permissions,  int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        boolean hasAllGranted = true;
//        for (int i = 0; i < grantResults.length; ++i) {
//            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
//                hasAllGranted = false;
//                //在用户已经拒绝授权的情况下，如果shouldShowRequestPermissionRationale返回false则
//                // 可以推断出用户选择了“不在提示”选项，在这种情况下需要引导用户至设置页手动授权
//                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
//                    //解释原因，并且引导用户至设置页手动授权
//                    new AlertDialog.Builder(this)
//                            .setMessage("【用户选择了不在提示按钮，或者系统默认不在提示（如MIUI）。" +
//                                    "引导用户到应用设置页去手动授权,注意提示用户具体需要哪些权限】\r\n" +
//                                    "获取相关权限失败:xxxxxx,将导致部分功能无法正常使用，需要到设置页面手动授权")
//                            .setPositiveButton("去授权", new DialogInterface.OnClickListener() {
//                                @Override
//                                public void onClick(DialogInterface dialog, int which) {
//                                    //引导用户至设置页手动授权
//                                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
//                                    Uri uri = Uri.fromParts("package", getApplicationContext().getPackageName(), null);
//                                    intent.setData(uri);
//                                    startActivity(intent);
//                                }
//                            })
//                            .setNegativeButton("取消", new DialogInterface.OnClickListener() {
//                                @Override
//                                public void onClick(DialogInterface dialog, int which) {
//                                    //引导用户手动授权，权限请求失败
//                                }
//                            }).setOnCancelListener(new DialogInterface.OnCancelListener() {
//                        @Override
//                        public void onCancel(DialogInterface dialog) {
//                            //引导用户手动授权，权限请求失败
//                        }
//                    }).show();
//
//                } else {
//                    //权限请求失败，但未选中“不再提示”选项
//                }
//                break;
//            }
//        }
//        if (hasAllGranted) {
//            //权限请求成功
//        }
//    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecorder();
        destroyTimer();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP) //申请录屏权限
    private void startCaptureIntent() {
        Intent captureIntent = mMediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION);
    }

    /**
     * 开始录制
     */
    private void startRecorder() {
        UnityPlayer.UnitySendMessage("GameManagerForRecord", "StartRecord", "");
        toast("开始录制");
        destroyTimer();
        initTimer();

        mTimer.schedule(mTimerTask, 0, 1000);
        state = RECORDING;
        if (mRecorder == null) return;
        mRecorder.start();
        registerReceiver(mStopActionReceiver, new IntentFilter(ACTION_STOP));
        // moveTaskToBack(true);
    }

    /**
     * 停止录制
     */
    private void stopRecorder() {
        UnityPlayer.UnitySendMessage("GameManagerForRecord", "StopRecord", "");
        isHandStop = true;
        toast("停止录制");
        Logger("----------------------" + "停止录制");
        mNotifications.clear();
        if (mRecorder != null) {
            mRecorder.quit();
        }
        mRecorder = null;
        try {
            unregisterReceiver(mStopActionReceiver);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        state = END;
    }

    private void cancelRecorder() {
        if (mRecorder == null) return;
        Toast.makeText(this, "Permission denied! Screen recorder is cancel", Toast.LENGTH_SHORT).show();
        stopRecorder();
    }

    @TargetApi(M)
    private void requestPermissions() {
        final String[] permissions = true ? new String[]{WRITE_EXTERNAL_STORAGE, RECORD_AUDIO}
                : new String[]{WRITE_EXTERNAL_STORAGE};
        boolean showRationale = false;
        for (String perm : permissions) {
            showRationale |= shouldShowRequestPermissionRationale(perm);
        }
        if (!showRationale) {
            requestPermissions(permissions, REQUEST_PERMISSIONS);
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage("Using your mic to record audio and your sd card to save video file")
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                requestPermissions(permissions, REQUEST_PERMISSIONS);
                            }
                        }
                )
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .show();
    }

    private boolean hasPermissions() {
        PackageManager pm = getPackageManager();
        String packageName = getPackageName();
        int granted = (true ? pm.checkPermission(RECORD_AUDIO, packageName) : PackageManager.PERMISSION_GRANTED)
                | pm.checkPermission(WRITE_EXTERNAL_STORAGE, packageName);
        return granted == PackageManager.PERMISSION_GRANTED;
    }

    private void toast(String message, Object... args) {
        final Toast toast = Toast.makeText(this,
                (args.length == 0) ? message : String.format(Locale.US, message, args),
                Toast.LENGTH_SHORT);
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    toast.show();
                }
            });
        } else {
            toast.show();
        }
    }

    private static String[] codecInfoNames(MediaCodecInfo[] codecInfos) {
        String[] names = new String[codecInfos.length];
        for (int i = 0; i < codecInfos.length; i++) {
            names[i] = codecInfos[i].getName();
        }
        return names;
    }

    /**
     * Print information of all MediaCodec on this device.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static void logCodecInfos(MediaCodecInfo[] codecInfos, String mimeType) {
        for (MediaCodecInfo info : codecInfos) {
            StringBuilder builder = new StringBuilder(512);
            MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(mimeType);
            builder.append("Encoder '").append(info.getName()).append('\'')
                    .append("\n  supported : ")
                    .append(Arrays.toString(info.getSupportedTypes()));
            MediaCodecInfo.VideoCapabilities videoCaps = caps.getVideoCapabilities();
            if (videoCaps != null) {
                builder.append("\n  Video capabilities:")
                        .append("\n  Widths: ").append(videoCaps.getSupportedWidths())
                        .append("\n  Heights: ").append(videoCaps.getSupportedHeights())
                        .append("\n  Frame Rates: ").append(videoCaps.getSupportedFrameRates())
                        .append("\n  Bitrate: ").append(videoCaps.getBitrateRange());
                if (VIDEO_AVC.equals(mimeType)) {
                    MediaCodecInfo.CodecProfileLevel[] levels = caps.profileLevels;

                    builder.append("\n  Profile-levels: ");
                    for (MediaCodecInfo.CodecProfileLevel level : levels) {
                        builder.append("\n  ").append(Utils.avcProfileLevelToString(level));
                    }
                }
                builder.append("\n  Color-formats: ");
                for (int c : caps.colorFormats) {
                    builder.append("\n  ").append(Utils.toHumanReadable(c));
                }
            }
            MediaCodecInfo.AudioCapabilities audioCaps = caps.getAudioCapabilities();
            if (audioCaps != null) {
                builder.append("\n Audio capabilities:")
                        .append("\n Sample Rates: ").append(Arrays.toString(audioCaps.getSupportedSampleRates()))
                        .append("\n Bit Rates: ").append(audioCaps.getBitrateRange())
                        .append("\n Max channels: ").append(audioCaps.getMaxInputChannelCount());
            }
            Log.i("@@@", builder.toString());
        }
    }

    private BroadcastReceiver mStopActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            File file = new File(mRecorder.getSavedPath());
            if (ACTION_STOP.equals(intent.getAction())) {
                stopRecorder();
            }
            Toast.makeText(context, "Recorder stopped!\n Saved file " + file, Toast.LENGTH_LONG).show();
            StrictMode.VmPolicy vmPolicy = StrictMode.getVmPolicy();
            try {
                // disable detecting FileUriExposure on public file
                StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
                viewResult(file);
            } finally {
                StrictMode.setVmPolicy(vmPolicy);
            }
        }

        private void viewResult(File file) {
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.addCategory(Intent.CATEGORY_DEFAULT);
            view.setDataAndType(Uri.fromFile(file), VIDEO_AVC);
            view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(view);
            } catch (ActivityNotFoundException e) {
                // no activity can open this video
            }
        }
    };


    /**
     * 隐藏statebar
     */
    public void StartBarShow() {
        runOnUiThread(new Runnable() {
            public void run() {
                MainActivity.this.setNoStatusBarNew();
            }
        });
    }

    public void ExitGame() {
        finish();
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    /**
     * 显示statebar
     */
    public void setStatusBarNew() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setStatusBarold(true);
            }
        });
    }

    /**
     * 隐藏stateBar
     */
    public void setNoStatusBarNew() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setStatusBarold(false);
            }
        });
    }

    public void setStatusBarold(boolean enbe) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {   //5.0及以上
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            if (enbe) {
                window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
            //根据上面设置是否对状态栏单独设置颜色
            if (useThemestatusBarColor) {
                window.setStatusBarColor(getResources().getColor(R.color.write));
            } else {
                window.setStatusBarColor(Color.TRANSPARENT);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {  //4.4到5.0
            WindowManager.LayoutParams localLayoutParams = getWindow().getAttributes();
            localLayoutParams.flags = (WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | localLayoutParams.flags);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && useStatusBarColor) {//android6.0以后可以对状态栏文字颜色和图标进行修改
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    //检查sd卡内存是否够用

    private volatile int i = 1;

    // handler类接收数据
    Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                i++;
                System.out.println("receive....");
            } else if (msg.what == 2) {
                stopRecorderAction();
            }
        }

        ;
    };



    // 线程类
    class ThreadShow implements Runnable {
        @Override
        public void run() {

            while (true && i * 1000 < DEFAULT_RECORDER_TIME) {
                try {
                    Thread.sleep(1000);
                    Message msg = new Message();
                    msg.what = 1;
                    handler.sendMessage(msg);
                    Logger("send..................." + Thread.currentThread().getName() + "......----------------------------------" + i);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("thread error...");
                }
            }
            if (i * 1000 >= DEFAULT_RECORDER_TIME) {
                Logger("-------------" + Thread.currentThread().getName() + "----------倒计时-停止录屏");
                Message msg = new Message();
                msg.what = 2;
                handler.sendMessage(msg);

            }
        }
    }


    private void Logger(String logMsg) {
        Log.d(TAG, logMsg);
    }

    //测试
    public void start(View view) {
        mNotifications.recording(10 * 1000);
        FileUtils.isHavaUsableSpace(this);
        startRecorderAndCheckPermissions();
    }

    //停止
    public void stop(View view) {
        stopRecorder();
    }

    @Override
    protected void onStop() {
        super.onStop();
       // UnityPlayer.UnitySendMessage("GameSetup", "SetRecordTog2false", "");
        //看不到界面，界面被遮挡
        if (!isHandStop && screenRecorderPath != null && !screenRecorderPath.equals("")) {
            File file = new File(screenRecorderPath);
            if (file.isFile()) {
                Logger("-----------------------是否移除成功" + file.delete());
            }
        }
    }

    /**
     * 获取SD 卡 是否可用
     * 参数1 可用空间
     * 参数2 总空间
     * 参数3 是否有足够的空间 400MB
     *
     * @return {"可用","总空间","false/true"}
     */
    public String[] getSdSpace(int limit) {
        return FileUtils.isHavaUsableSpace(this, limit);
    }

    public String getSdSpaceString(int limit) {
        String[] strings = FileUtils.isHavaUsableSpace(this, limit);
        return strings[0] + ";" + strings[1] + ";" + strings[2];
    }

    /**
     * 默认300Mb 空间
     *
     * @return
     */
    public String[] getSdSpace() {
        return FileUtils.isHavaUsableSpace(this);
    }

    /**
     * 获取录屏状态
     *
     * @return
     */
    public int getRecorderState() {
        return state;
    }

    Timer mTimer;
    TimerTask mTimerTask;
    long curTime = DEFAULT_RECORDER_TIME;
    int WHAT = 1;

    //初始化timer
    public void initTimer() {
        mTimerTask = new TimerTask() {
            @Override
            public void run() {
                if (curTime == 0) {
                    curTime = DEFAULT_RECORDER_TIME;
                } else {
                    //计数器，每次减一秒。
                    curTime -= 1000;
                }
                Logger("send..................." + Thread.currentThread().getName() + "......----------------------------------" + i);
                Message message = new Message();
                message.what = WHAT;
                message.obj = curTime;
                mHandler.sendMessage(message);
            }
        };
        mTimer = new Timer();
    }

    //实现更新主线程UI
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    long sRecLen = (long) msg.obj;
                    if (sRecLen <= 0) {
                        mTimer.cancel();
                        curTime = 0;
                        //停止录制
                        stopRecorderAction();
                    }
                    break;
            }
        }
    };

    @Override
    public void setRequestedOrientation(int requestedOrientation) {
        super.setRequestedOrientation(requestedOrientation);
    }

    /**
     * destory上次使用的 Timer
     */
    public void destroyTimer() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        if (mTimerTask != null) {
            mTimerTask.cancel();
            mTimerTask = null;
        }
        Logger("----------------------" + "去除mTime");
    }


}
