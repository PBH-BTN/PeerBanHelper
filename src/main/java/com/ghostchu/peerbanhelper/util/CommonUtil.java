package com.ghostchu.peerbanhelper.util;

import io.javalin.http.Context;
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

    public static String userIp(Context context) {
        String ip = context.header("CF-Connecting-IP");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = context.header("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = context.header("X-Forwarded-For");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = context.header("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = context.header("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = context.ip();
        }
        return ip;
    }

    @NotNull
    public static String getClassJarPath(@NotNull Class<?> clazz) {
        return CommonUtil.getClassPath(clazz);
    }

}
