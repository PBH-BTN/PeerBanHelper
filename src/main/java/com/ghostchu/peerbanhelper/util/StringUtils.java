package com.ghostchu.peerbanhelper.util;

public class StringUtils {

    /**
     * 截取 original 中在 after 之后且在 before 之前的字符串。
     * 如果 after 或 before 不存在，则返回 null。
     */
    public static String substring(String original, String after, String before) {
        if (original == null || after == null || before == null) return null;

        int start = original.indexOf(after);
        if (start == -1) return null;

        // 实际内容的起始位置应该是 after 字符串之后
        start += after.length();

        int end = original.indexOf(before, start);
        if (end == -1) return null;

        return original.substring(start, end);
    }

    /**
     * 截取 original 中第一个 before 之前的字符串。
     * 如果未找到 before，则返回原字符串。
     */
    public static String substringBefore(String original, String before) {
        if (original == null || before == null) return original;
        
        int index = original.indexOf(before);
        if (index == -1) return original;

        return original.substring(0, index);
    }

    /**
     * 截取 original 中第一个 after 之后的字符串。
     * 如果未找到 after，则返回原字符串。
     */
    public static String substringAfter(String original, String after) {
        if (original == null || after == null) return original;

        int index = original.indexOf(after);
        if (index == -1) return original;

        return original.substring(index + after.length());
    }

}