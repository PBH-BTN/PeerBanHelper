package com.ghostchu.peerbanhelper.util;

import com.ghostchu.peerbanhelper.Main;
import io.javalin.http.Context;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

    public static boolean isUsingReserveProxy(Context context) {
        var list = List.of("X-Real-IP", "X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP");
        for (String s : list) {
            if (context.header(s) != null)
                return true;
        }
        return false;
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

    @SneakyThrows
    public static List<File> readAllResFiles(String path) {
        List<File> files = new ArrayList<>();
        var urlEnumeration = Main.class.getClassLoader().getResources(path);
        while (urlEnumeration.hasMoreElements()) {
            var url = urlEnumeration.nextElement();
            var fileDir = new File(new URI(url.toString()));
            files.addAll(recursiveReadFile(fileDir));
        }
        return files;
    }

    @SneakyThrows
    public static File readResFile(String path) {
        var urlEnumeration = Main.class.getClassLoader().getResources(path);
        if (urlEnumeration.hasMoreElements()) {
            var url = urlEnumeration.nextElement();
            return new File(new URI(url.toString()));
        }
        return null;
    }

    public static ZoneOffset getSystemZoneOffset() {
        return ZoneId.systemDefault().getRules().getOffset(Instant.now());
    }

    public static long getStartOfToday(long time) {
        Instant instant = Instant.now();
        ZoneId systemZone = ZoneId.systemDefault();
        ZoneOffset currentOffsetForMyZone = systemZone.getRules().getOffset(instant);
        LocalDate parse = Instant.ofEpochMilli(time).atZone(systemZone).toLocalDate();
        return parse.atStartOfDay().toInstant(currentOffsetForMyZone).toEpochMilli();
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
}
