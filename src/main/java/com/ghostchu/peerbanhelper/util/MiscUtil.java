package com.ghostchu.peerbanhelper.util;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

public final class MiscUtil {
    public static final Object EMPTY_OBJECT = new Object();


    public static void gzip(InputStream is, OutputStream os) throws IOException {
        GZIPOutputStream gzipOs = new GZIPOutputStream(os);
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) > -1) {
            gzipOs.write(buffer, 0, bytesRead);
        }
        gzipOs.close();
    }

    /**
     * Get this class available or not
     *
     * @param qualifiedName class qualifiedName
     * @return boolean Available
     */
    public static boolean isClassAvailable(@NotNull String qualifiedName) {
        try {
            Class.forName(qualifiedName);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static List<File> recursiveReadFile(File fileOrDir) {
        List<File> files = new ArrayList<>();
        if (fileOrDir == null) {
            return files;
        }

        if (fileOrDir.isFile()) {
            files.add(fileOrDir);
        } else {
            for (var file : Objects.requireNonNull(fileOrDir.listFiles())) {
                files.addAll(recursiveReadFile(file));
            }
        }
        return files;
    }

    public static boolean is64BitJVM() {
        // 优先检查 sun.arch.data.model（直接指明位数）
        String dataModel = System.getProperty("sun.arch.data.model");
        if (dataModel != null) {
            return "64".equals(dataModel);
        }
        // 检查已知的 64 位架构名称
        String arch = System.getProperty("os.arch");
        List<String> arch64 = Arrays.asList("x86_64", "amd64", "aarch64", "ppc64", "ppc64le", "s390x", "sparcv9", "ia64");
        if (arch64.contains(arch)) {
            return true;
        }
        // 后备检查：虚拟机名称是否包含 "64"
        String vmName = System.getProperty("java.vm.name", "").toLowerCase();
        return vmName.contains("64");
    }

    @SafeVarargs
    public static <T> T[] concatAll(T[] first, T[]... rest) {
        int totalLength = first.length;

        for (T[] array : rest) {
            totalLength += array.length;
        }

        T[] result = Arrays.copyOf(first, totalLength);
        int offset = first.length;

        for (T[] array : rest) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }

        return result;
    }

    public static int randomAvailablePort() {
        try {
            var tmpSocket = new ServerSocket(0);
            var localPort = tmpSocket.getLocalPort();
            tmpSocket.close();
            return localPort;
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean removeBeeCPShutdownHook(Object dataSource) {
        try {
            Class<?> dataSourceClass = dataSource.getClass();
            Field poolField = dataSourceClass.getDeclaredField("pool");
            poolField.setAccessible(true);
            Object poolObj = poolField.get(dataSource);

            Class<?> poolClass = poolObj.getClass();
            Field hookField = poolClass.getDeclaredField("exitHook");
            hookField.setAccessible(true);
            Thread hookObj = (Thread) hookField.get(poolObj);

            Runtime.getRuntime().removeShutdownHook(hookObj);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return false;
    }
}
