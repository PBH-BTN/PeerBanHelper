package com.ghostchu.peerbanhelper.util;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class StrUtil {
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

    public static String toStringHex(String s) {
        byte[] baKeyword = new byte[s.length() / 2];
        for (int i = 0; i < baKeyword.length; i++) {
            baKeyword[i] = (byte) (0xff & Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16));
        }
        return new String(baKeyword, StandardCharsets.ISO_8859_1);
    }
}
