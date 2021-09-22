package com.hutcwp.plugin;

/**
 * Description:
 * <p>
 * Created by n24314 on 2021/9/22. E-mail: caiwenpeng@corp.netease.com
 */
public class MLog {

    private static boolean isDebug = true;

    private static void setIsDebug(boolean debug) {
        isDebug = debug;
    }

    public static void info(String msg) {
        System.out.println(msg);
    }

    public static void debug(String msg) {
        if (isDebug) {
            System.out.println(msg);
        }
    }

}
