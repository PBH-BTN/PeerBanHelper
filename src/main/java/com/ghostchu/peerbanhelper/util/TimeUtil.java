package com.ghostchu.peerbanhelper.util;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class TimeUtil {

    @NotNull
    public static OffsetDateTime zeroOffsetDateTime() {
        return Instant.ofEpochMilli(0).atOffset(ZoneOffset.UTC);
    }

    @NotNull
    public static OffsetDateTime fromMillis(long millis) {
        return Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC);
    }

    public static long toMillis(@NotNull OffsetDateTime dateTime) {
        return dateTime.toInstant().toEpochMilli();
    }
}
