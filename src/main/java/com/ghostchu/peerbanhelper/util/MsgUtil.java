package com.ghostchu.peerbanhelper.util;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.management.LockInfo;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.text.CharacterIterator;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.text.StringCharacterIterator;

public final class MsgUtil {
    private static final DecimalFormat df = new DecimalFormat("0.00%");
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static String escapeSql(String sql){
        if(sql == null) return null;
        return StringUtils.replace(sql, "'", "''");
    }

    public static String humanReadableByteCountBin(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format("%.1f %ciB", value / 1024.0, ci.current());
    }

    public static String humanReadableByteCountSI(long bytes) {
        if (-1000 < bytes && bytes < 1000) {
            return bytes + " B";
        }
        CharacterIterator ci = new StringCharacterIterator("kMGTPE");
        while (bytes <= -999_950 || bytes >= 999_950) {
            bytes /= 1000;
            ci.next();
        }
        return String.format("%.1f %cB", bytes / 1000.0, ci.current());
    }

    public static String threadInfoToString(ThreadInfo info) {
        StringBuilder sb = new StringBuilder("\"" + info.getThreadName() + "\"" +
                                             (info.isDaemon() ? " daemon" : "") +
                                             " prio=" + info.getPriority() +
                                             " Id=" + info.getThreadId() + " " +
                                             info.getThreadState());
        if (info.getLockName() != null) {
            sb.append(" on ").append(info.getLockName());
        }
        if (info.getLockOwnerName() != null) {
            sb.append(" owned by \"").append(info.getLockOwnerName()).append("\" Id=").append(info.getLockOwnerId());
        }
        if (info.isSuspended()) {
            sb.append(" (suspended)");
        }
        if (info.isInNative()) {
            sb.append(" (in native)");
        }
        sb.append('\n');
        int i = 0;
        for (; i < info.getStackTrace().length && i < 500; i++) {
            StackTraceElement ste = info.getStackTrace()[i];
            sb.append("\tat ").append(ste.toString());
            sb.append('\n');
            if (i == 0 && info.getLockInfo() != null) {
                Thread.State ts = info.getThreadState();
                switch (ts) {
                    case BLOCKED:
                        sb.append("\t-  blocked on ").append(info.getLockInfo());
                        sb.append('\n');
                        break;
                    case WAITING, TIMED_WAITING:
                        sb.append("\t-  waiting on ").append(info.getLockInfo());
                        sb.append('\n');
                        break;
                    default:
                }
            }

            for (MonitorInfo mi : info.getLockedMonitors()) {
                if (mi.getLockedStackDepth() == i) {
                    sb.append("\t-  locked ").append(mi);
                    sb.append('\n');
                }
            }
        }
        if (i < info.getStackTrace().length) {
            sb.append("\t...");
            sb.append('\n');
        }

        LockInfo[] locks = info.getLockedSynchronizers();
        if (locks.length > 0) {
            sb.append("\n\tNumber of locked synchronizers = ").append(locks.length);
            sb.append('\n');
            for (LockInfo li : locks) {
                sb.append("\t- ").append(li);
                sb.append('\n');
            }
        }
        sb.append('\n');
        return sb.toString();
    }

    public static DecimalFormat getPercentageFormatter() {
        return df;
    }

    public static SimpleDateFormat getDateFormatter() {
        return sdf;
    }

    /**
     * Replace args in raw to args
     *
     * @param raw  text
     * @param args args
     * @return filled text
     */
    @NotNull
    public static String fillArgs(@Nullable String raw, @Nullable String... args) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        int start = 0;
        int argIndex = 0;

        while (start < raw.length()) {
            int placeholderIndex = raw.indexOf("{}", start);
            if (placeholderIndex == -1) {
                result.append(raw.substring(start));
                break;
            }
            result.append(raw, start, placeholderIndex);
            if (args != null && argIndex < args.length) {
                result.append(args[argIndex] != null ? args[argIndex] : "");
                argIndex++;
            } else {
                result.append("{}");
            }
            start = placeholderIndex + 2;
        }
        return result.toString();
    }
}
