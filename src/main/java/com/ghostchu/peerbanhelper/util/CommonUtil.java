package com.ghostchu.peerbanhelper.util;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 特殊工具类，不得依赖任何外部类
 */
@Slf4j
public final class CommonUtil {

    private static final ScheduledExecutorService GENERAL_SCHEDULER = Executors.newScheduledThreadPool(8, Thread.ofPlatform().name("CommonScheduler").factory());

    public static ScheduledExecutorService getScheduler() {
        return GENERAL_SCHEDULER;
    }

    public static void deleteFileOrDirectory(@NotNull File file) {
        // Traverse the file tree in depth-first fashion and delete each file/directory.
        if(!file.exists()) return;
        if(file.isFile()){
            file.delete();
            return;
        }
        try (var stream = Files.walk(file.toPath())){
                    stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("Failed to delete file or directory: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to delete file or directory: {}", file, e);
        }
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
