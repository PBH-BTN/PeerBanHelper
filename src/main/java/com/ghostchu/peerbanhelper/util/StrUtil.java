package com.ghostchu.peerbanhelper.util;

import java.util.Locale;

public class StrUtil {
    public static String toLowerCaseRoot(String str) {
        return str.toLowerCase(Locale.ROOT);
    }

    public static String toUpperCaseRoot(String str) {
        return str.toUpperCase(Locale.ROOT);
    }

    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
