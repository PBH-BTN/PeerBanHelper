package com.ghostchu.peerbanhelper.util;

import io.javalin.http.Context;

import java.sql.Timestamp;

public final class WebUtil {

    public static TimeQueryModel parseTimeQueryModel(Context ctx) throws IllegalArgumentException {
        var startAtStr = ctx.queryParam("startAt");
        var endAtStr = ctx.queryParam("endAt");
        if (startAtStr == null) {
            throw new IllegalArgumentException("startAt cannot be null");
        }
        if (endAtStr == null) {
            throw new IllegalArgumentException("endAt cannot be null");
        }
        Timestamp startAt = new Timestamp(Long.parseLong(startAtStr));
        Timestamp endAt = new Timestamp(Long.parseLong(endAtStr));
        return new TimeQueryModel(startAt, endAt);
    }

    public record TimeQueryModel(
            Timestamp startAt,
            Timestamp endAt
    ) {
    }
}
