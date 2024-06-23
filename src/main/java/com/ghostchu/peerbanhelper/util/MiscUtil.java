package com.ghostchu.peerbanhelper.util;

import com.ghostchu.peerbanhelper.Main;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MiscUtil {
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
}
