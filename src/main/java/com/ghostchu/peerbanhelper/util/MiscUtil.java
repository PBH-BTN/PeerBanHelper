package com.ghostchu.peerbanhelper.util;

import com.ghostchu.peerbanhelper.Main;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

public class MiscUtil {
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

    public static Long getStartOfToday(long time) {
        Instant instant = Instant.now();
        ZoneId systemZone = ZoneId.systemDefault();
        ZoneOffset currentOffsetForMyZone = systemZone.getRules().getOffset(instant);
        LocalDate parse = Instant.ofEpochMilli(time).atZone(systemZone).toLocalDate();
        return parse.atStartOfDay().toInstant(currentOffsetForMyZone).toEpochMilli();
    }


}
