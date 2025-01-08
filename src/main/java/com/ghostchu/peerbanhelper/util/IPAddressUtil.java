package com.ghostchu.peerbanhelper.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
/**
 * IP 地址工具类
 */
public class IPAddressUtil {
    private static final IPAddress INVALID_ADDRESS_MISSINGNO = new IPAddressString("127.123.123.123").getAddress();
    private static final Cache<String, IPAddress> IP_ADDRESS_CACHE = CacheBuilder.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .maximumSize(100)
            .softValues()
            .build();
    private static final Cache<IPAddress, IPAddress> IP_WITHOUT_PREFIX_CACHE = CacheBuilder.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .maximumSize(100)
            .softValues()
            .build();

    /**
     * Converts a string representation of an IP address to an IPAddress object with automatic IPv4-in-IPv6 extraction.
     *
     * This method handles various IP address formats, including bracketed IPv6 addresses, and performs caching
     * to improve performance. If the input IP address is an IPv6 address that can be converted to IPv4, it will
     * automatically convert the address.
     *
     * @param ip The IP address string to convert. Can be null, IPv4, or IPv6 format.
     * @return An IPAddress object representing the converted IP address, or null if input is null.
     *         Returns a predefined invalid address if conversion fails.
     *
     * @throws ExecutionException If there is an error during cache retrieval or address conversion
     */
    @Contract(value = "null -> null", pure = true)
    public static IPAddress getIPAddress(String ip) {
        if (ip == null) return null;
        if (ip.startsWith("[") && ip.endsWith("]")) {
            ip = ip.substring(1, ip.length() - 1);
        }
        final String ipFinal = ip;
        try {
            return IP_ADDRESS_CACHE.get(ip, () -> {
                IPAddress ipAddress = new IPAddressString(ipFinal).toAddress();
                if (ipAddress.isIPv4Convertible()) {
                    ipAddress = ipAddress.toIPv4();
                }
                return ipAddress;
            });
        } catch (ExecutionException e) {
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
            return IP_ADDRESS_CACHE.get(ip, () -> {
                return new IPAddressString(ip).toAddress();
            });
        } catch (ExecutionException e) {
            log.error("Unable to get ipaddress from ip {}", ip, e);
            return null;
        }
    }

    @NotNull
    public static IPAddress toPrefixBlock(IPAddress ipAddress, int length) {
        try {
            return IP_WITHOUT_PREFIX_CACHE.get(ipAddress, () -> ipAddress.toPrefixBlock(length).toZeroHost());
        } catch (ExecutionException e) {
            log.error("Unable to get ipaddress with prefixblock for ip {}", ipAddress, e);
            return ipAddress;
        }
    }
}
