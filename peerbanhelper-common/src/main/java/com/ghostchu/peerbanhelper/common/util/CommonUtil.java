package com.ghostchu.peerbanhelper.common.util;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 特殊工具类，不得依赖任何外部类
 */
public final class CommonUtil {

    private static final ScheduledExecutorService GENERAL_SCHEDULER = Executors.newScheduledThreadPool(8, Thread.ofVirtual().factory());

    public static ScheduledExecutorService getScheduler() {
        return GENERAL_SCHEDULER;
    }

    @NotNull
    public static String getClassPath(@NotNull Class<?> clazz) {
        String jarPath = clazz.getProtectionDomain().getCodeSource().getLocation().getFile();
        jarPath = URLDecoder.decode(jarPath, StandardCharsets.UTF_8);
        return jarPath;
    }

    @NotNull
    public static File getAppJarFile(@NotNull Class<?> clazz) throws FileNotFoundException {
        String path = getClassJarPath(clazz);
        File file = new File(path);
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + path);
        }
        return file;
    }


    @NotNull
    public static String getClassJarPath(@NotNull Class<?> clazz) {
        return CommonUtil.getClassPath(clazz);
    }

}
