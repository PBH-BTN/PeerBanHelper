package com.ghostchu.peerbanhelper.util;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class StrUtil {
    @Nullable
    public static String toLowerCaseRoot(@Nullable String str) {
        if (str == null) return null;
        return str.toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static String toUpperCaseRoot(@Nullable String str) {
        if (str == null) return null;
        return str.toUpperCase(Locale.ROOT);
    }

    public static boolean isEmpty(@Nullable String str) {
        return str == null || str.isEmpty();
    }

    public static boolean isBlank(@Nullable String str) {
        return str == null || str.trim().isEmpty();
    }
}
