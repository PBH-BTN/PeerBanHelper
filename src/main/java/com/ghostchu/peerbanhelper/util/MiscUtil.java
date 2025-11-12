package com.ghostchu.peerbanhelper.util;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.text.SimpleDateFormat;
import java.time.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

public final class MiscUtil {
    public static final Object EMPTY_OBJECT = new Object();
    public static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public static final SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm:ss");
    public static final SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");

    public static String formatDateOnly(long timestamp) {
        return sdfDate.format(new Date(timestamp));
    }

    public static String formatDateTime(long timestamp) {
        return sdf.format(new Date(timestamp));
    }

    public static String formatTimeOnly(long timestamp) {
        return sdfTime.format(new Date(timestamp));
    }


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

    public static ZoneOffset getSystemZoneOffset() {
        return ZoneId.systemDefault().getRules().getOffset(Instant.now());
    }

    public static long getStartOfToday(long time) {
//        Instant instant = Instant.now();
//        ZoneId systemZone = ZoneId.systemDefault();
//        ZoneOffset currentOffsetForMyZone = systemZone.getRules().getOffset(instant);
//        LocalDate parse = Instant.ofEpochMilli(time).atZone(systemZone).toLocalDate();
//        return parse.atStartOfDay().toInstant(currentOffsetForMyZone).toEpochMilli();
        ZoneId systemZone = ZoneId.systemDefault();
        ZoneOffset currentOffsetForMyZone = systemZone.getRules().getOffset(Instant.ofEpochMilli(time));
        LocalDate date = Instant.ofEpochMilli(time).atZone(systemZone).toLocalDate();
        return date.atStartOfDay().toInstant(currentOffsetForMyZone).toEpochMilli();
    }

    public static long getEndOfToday(long time) {
        ZoneId systemZone = ZoneId.systemDefault();
        ZoneOffset currentOffsetForMyZone = systemZone.getRules().getOffset(Instant.now());
        // 转换为该时区的 LocalDateTime
        LocalDateTime dateTime = Instant.ofEpochMilli(time).atZone(systemZone).toLocalDateTime();
        // 获取当天的结束
        LocalDateTime dayEnd = dateTime.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        // 转换回时间戳
        return dayEnd.toInstant(currentOffsetForMyZone).toEpochMilli();
    }

    public static long getStartOfHour(long time) {
        ZoneId systemZone = ZoneId.systemDefault();
        ZoneOffset currentOffsetForMyZone = systemZone.getRules().getOffset(Instant.now());
        // 转换为该时区的 LocalDateTime
        LocalDateTime dateTime = Instant.ofEpochMilli(time).atZone(systemZone).toLocalDateTime();
        // 获取当前小时的开始（分钟和秒置为0）
        LocalDateTime hourStart = dateTime.withMinute(0).withSecond(0).withNano(0);
        // 转换回时间戳
        return hourStart.toInstant(currentOffsetForMyZone).toEpochMilli();
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
}
