package com.ghostchu.peerbanhelper.util;

public class IpUtil {
    public static int ipStr2Int(String ip) throws IllegalArgumentException {
        try {
            String[] ips = ip.split("\\.");
            return Integer.parseInt(ips[0]) << 24 | Integer.parseInt(ips[1]) << 16
                    | Integer.parseInt(ips[2]) << 8 | Integer.parseInt(ips[3]);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("trans ip address failed: " + ip, e);
        }
    }

    public static String int2IpStr(int ip) {
        return String.format("%d.%d.%d.%d", (ip >>> 24) & 0xff, (ip >>> 16) & 0xff, (ip >>> 8) & 0xff, ip & 0xff);
    }
}
