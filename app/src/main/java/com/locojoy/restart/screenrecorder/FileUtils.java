package com.locojoy.restart.screenrecorder;

import android.content.Context;
import android.os.Environment;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

public class FileUtils {

    /**
     * 是否有Sd卡进行挂载
     *
     * @return
     */
    public static boolean isHaveMediaMounted() {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            return true;
        }
        return false;
    }

    /**
     * 剩余控件
     * 参数1 可用空间
     * 参数2 总空间
     * 参数3 是否有足够的空间 400MB
     * return new String[]{"0","0","true/false"}
     *
     * @param context
     */
    public static String[] isHavaUsableSpace(Context context, long space) {
        long limitSpace = (space == 0 ? 1024 * 1024 * 300 : space);
        return getHavaSpace(context, limitSpace);
    }

    private static String[] getHavaSpace(Context context, long limitSpace) {
        if (isHaveMediaMounted()) {
            File sdcard_filedir = Environment.getExternalStorageDirectory();//得到sdcard的目录作为一个文件对象
            long usableSpace = sdcard_filedir.getUsableSpace();//获取文件目录对象剩余空间
            long totalSpace = sdcard_filedir.getTotalSpace();
            //将一个long类型的文件大小格式化成用户可以看懂的M，G字符串
            String usableSpace_str = Formatter.formatFileSize(context, usableSpace);
            String totalSpace_str = Formatter.formatFileSize(context, totalSpace);
            if (usableSpace < limitSpace) {
                //判断剩余空间是否小于200M
                Toast.makeText(context, "sdcard剩余空间不足,无法满足下载；剩余空间为：" + usableSpace_str + "总空间：" + totalSpace_str, Toast.LENGTH_SHORT).show();
                return new String[]{String.valueOf(usableSpace), String.valueOf(totalSpace), "false"};
            } else {
                Log.i("FileUtils", "空间多于 400MB");
                return new String[]{String.valueOf(usableSpace), String.valueOf(totalSpace), "true"};
            }

        } else {
            Toast.makeText(context, "未找到Sd卡", Toast.LENGTH_SHORT).show();
        }
        return new String[]{"0", "0", "false"};
    }

    public static String[] isHavaUsableSpace(Context context) {
        return getHavaSpace(context, 0);
    }


}
