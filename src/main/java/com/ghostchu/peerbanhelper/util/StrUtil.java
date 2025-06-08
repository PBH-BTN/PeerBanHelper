package com.ghostchu.peerbanhelper.util;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public final class StrUtil {
    private static final Pattern capitalPattern = Pattern.compile("^.");
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

    public static String uncapitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str);
        sb.setCharAt(0, Character.toLowerCase(sb.charAt(0)));
        return sb.toString();
    }

    public static String capitalize(String simpleName) {
        return capitalPattern.matcher(simpleName).replaceFirst(m -> m.group().toUpperCase());
    }
}
