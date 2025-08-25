package com.ghostchu.peerbanhelper.util;

import inet.ipaddr.IPAddress;
import io.javalin.http.Context;

import java.sql.Timestamp;
import java.util.List;

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

    public static boolean isUsingReserveProxy(Context context) {
        var list = List.of("X-Real-IP", "X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP");
        for (String s : list) {
            if (context.header(s) != null)
                return true;
        }
        return false;
    }

    public static String userIp(Context context) {
        IPAddress ipAddress = IPAddressUtil.getIPAddress(context.ip());
        if (ipAddress.isAnyLocal() || ipAddress.isLoopback() || ipAddress.isLocal()) {
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
        return context.ip();
    }

    public record TimeQueryModel(
            Timestamp startAt,
            Timestamp endAt
    ) {
    }
}
