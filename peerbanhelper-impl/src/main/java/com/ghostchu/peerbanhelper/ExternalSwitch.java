package com.ghostchu.peerbanhelper;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class ExternalSwitch {
    public static String parse(String args, @Nullable String def) {
        var value = System.getProperty(args);
        if (value == null)
            value = System.getenv(args.replace(".", "_").replace("-", "_").toUpperCase(Locale.ROOT));
        if (value == null)
            return def;
        if (Main.getStartupArgs() != null) {
            for (var arg : Main.getStartupArgs()) {
                if (arg.startsWith(args + "=")) {
                    value = arg.substring(args.length() + 1);
                    break;
                }
            }
        }
        return value;
    }

    public static boolean parseBoolean(String args, boolean def) {
        var value = parse(args, null);
        if (value == null)
            return def;
        return Boolean.parseBoolean(value);
    }

    public static int parseInt(String args, int def) {
        var value = parse(args, null);
        if (value == null)
            return def;
        return Integer.parseInt(value);
    }

    public static long parseLong(String args, long def) {
        var value = parse(args, null);
        if (value == null)
            return def;
        return Long.parseLong(value);
    }

    public static double parseDouble(String args, double def) {
        var value = parse(args, null);
        if (value == null)
            return def;
        return Double.parseDouble(value);
    }

    public static float parseFloat(String args, float def) {
        var value = parse(args, null);
        if (value == null)
            return def;
        return Float.parseFloat(value);
    }

    public static short parseShort(String args, short def) {
        var value = parse(args, null);
        if (value == null)
            return def;
        return Short.parseShort(value);
    }

    public static byte parseByte(String args, byte def) {
        var value = parse(args, null);
        if (value == null)
            return def;
        return Byte.parseByte(value);
    }

    public static char parseChar(String args, char def) {
        var value = parse(args, null);
        if (value == null)
            return def;
        return value.charAt(0);
    }

    public static String parse(String args) {
        return parse(args, null);
    }

    public static boolean parseBoolean(String args) {
        return parseBoolean(args, false);
    }

    public static int parseInt(String args) {
        return parseInt(args, 0);
    }

    public static long parseLong(String args) {
        return parseLong(args, 0);
    }

    public static double parseDouble(String args) {
        return parseDouble(args, 0);
    }

    public static float parseFloat(String args) {
        return parseFloat(args, 0);
    }

    public static short parseShort(String args) {
        return parseShort(args, (short) 0);
    }

    public static byte parseByte(String args) {
        return parseByte(args, (byte) 0);
    }

    public static char parseChar(String args) {
        return parseChar(args, '\u0000');
    }
}
