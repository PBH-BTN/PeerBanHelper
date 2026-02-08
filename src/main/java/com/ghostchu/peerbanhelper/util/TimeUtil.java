package com.ghostchu.peerbanhelper.util;

import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.time.*;
import java.util.Date;

public class TimeUtil {

    public static final OffsetDateTime zeroOffsetDateTime = Instant.ofEpochMilli(0).atOffset(ZoneOffset.UTC);
    public static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public static final SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm:ss");
    public static final SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");

    @NotNull
    public static OffsetDateTime fromMillis(long millis) {
        return Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC);
    }

    public static long toMillis(@NotNull OffsetDateTime dateTime) {
        return dateTime.toInstant().toEpochMilli();
    }

    public static String formatDateOnly(long timestamp) {
        return sdfDate.format(new Date(timestamp));
    }

    public static String formatDateTime(long timestamp) {
        return sdf.format(new Date(timestamp));
    }

    public static String formatTimeOnly(long timestamp) {
        return sdfTime.format(new Date(timestamp));
    }

    public static ZoneOffset getSystemZoneOffset() {
        return ZoneId.systemDefault().getRules().getOffset(Instant.now());
    }

    public static OffsetDateTime getStartOfToday(long time) {
        ZoneId systemZone = ZoneId.systemDefault();
        ZoneOffset currentOffsetForMyZone = systemZone.getRules().getOffset(Instant.ofEpochMilli(time));
        LocalDate date = Instant.ofEpochMilli(time).atZone(systemZone).toLocalDate();
        return date.atStartOfDay().atOffset(currentOffsetForMyZone);
    }

    public static OffsetDateTime getStartOfToday(OffsetDateTime time) {
        ZoneId systemZone = ZoneId.systemDefault();
        ZoneOffset currentOffsetForMyZone = systemZone.getRules().getOffset(time.toInstant());
        return time.toLocalDate().atStartOfDay().atOffset(currentOffsetForMyZone);
    }

    public static OffsetDateTime getEndOfToday(long time) {
        ZoneId systemZone = ZoneId.systemDefault();
        ZoneOffset currentOffsetForMyZone = systemZone.getRules().getOffset(Instant.now());
        // 转换为该时区的 LocalDateTime
        LocalDateTime dateTime = Instant.ofEpochMilli(time).atZone(systemZone).toLocalDateTime();
        // 获取当天的结束
        LocalDateTime dayEnd = dateTime.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        // 转换回时间戳
        return dayEnd.atOffset(currentOffsetForMyZone);
    }

    public static OffsetDateTime getStartOfHour(long time) {
        ZoneId systemZone = ZoneId.systemDefault();
        ZoneOffset currentOffsetForMyZone = systemZone.getRules().getOffset(Instant.now());
        // 转换为该时区的 LocalDateTime
        LocalDateTime dateTime = Instant.ofEpochMilli(time).atZone(systemZone).toLocalDateTime();
        // 获取当前小时的开始（分钟和秒置为0）
        LocalDateTime hourStart = dateTime.withMinute(0).withSecond(0).withNano(0);
        // 转换回时间戳
        return hourStart.atOffset(currentOffsetForMyZone);
    }
}
