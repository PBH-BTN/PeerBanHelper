package com.ghostchu.peerbanhelper.util;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 特殊工具类，不得依赖任何外部类
 */
public class CommonUtil {

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
