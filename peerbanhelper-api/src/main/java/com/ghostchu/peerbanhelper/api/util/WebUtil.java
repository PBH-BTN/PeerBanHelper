package com.ghostchu.peerbanhelper.api.util;

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


    public static String userIp(Context context) {
        String ip = context.header("CF-Connecting-IP");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = context.header("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = context.header("X-Forwarded-For");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = context.header("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = context.header("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = context.ip();
        }
        return ip;
    }

    public record TimeQueryModel(
            Timestamp startAt,
            Timestamp endAt
    ) {
    }
}
