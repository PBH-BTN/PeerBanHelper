package com.ghostchu.peerbanhelper.util;

import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
/**
 * IP 地址工具类
 */
public final class IPAddressUtil {
    private static final IPAddress INVALID_ADDRESS_MISSINGNO = new IPAddressString("127.123.123.123").getAddress();

    /**
     * 将字符串转换为 IPAddress 对象，并自动进行 IPV4 in IPV6 提取转换
     *
     * @param ip
     * @return
     */
    @Contract(value = "null -> null", pure = true)
    public static IPAddress getIPAddress(String ip) {
        if (ip == null) return null;
        if (ip.startsWith("[") && ip.endsWith("]")) {
            ip = ip.substring(1, ip.length() - 1);
        }
        final String ipFinal = ip;
        try {
            IPAddress ipAddress = new IPAddressString(ipFinal).toAddress();
            if (ipAddress.isIPv4Convertible()) {
                ipAddress = ipAddress.toIPv4();
            }
            return ipAddress;
        } catch (AddressStringException e) {
            log.error("Unable to get ipaddress from ip {}", ipFinal, e);
            assert false;
            return INVALID_ADDRESS_MISSINGNO;
        }
    }

    /**
     * 将字符串转换为 IPAddress 对象，并自动进行 IPV4 in IPV6 提取转换
     *
     * @param ip
     * @return
     */
    @Nullable
    public static IPAddress getIPAddressNoAutoConversation(String ip) {
        try {
            return new IPAddressString(ip).toAddress();
        } catch (AddressStringException e) {
            log.error("Unable to get ipaddress from ip {}", ip, e);
            return INVALID_ADDRESS_MISSINGNO;
        }
    }

    @NotNull
    public static IPAddress toPrefixBlock(IPAddress ipAddress, int length) {
        return ipAddress.toPrefixBlock(length).toZeroHost();
    }
}
