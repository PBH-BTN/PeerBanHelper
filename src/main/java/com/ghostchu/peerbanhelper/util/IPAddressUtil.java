package com.ghostchu.peerbanhelper.util;

import com.ghostchu.peerbanhelper.Main;
import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

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
        try {
            IPAddress ipAddress = new IPAddressString(ip).toAddress();
            if (ipAddress.isIPv4Convertible()) {
                ipAddress = ipAddress.toIPv4();
            }
            return ipAddress;
        } catch (AddressStringException e) {
            log.error("Unable to get ipaddress from ip {}", ip, e);
            return INVALID_ADDRESS_MISSINGNO;
        } catch (Exception e) {
            Sentry.captureException(e);
            log.error("Unable to get ipaddress from ip {} because an unknown error, returning default.", ip, e);
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

    public static String adaptIP(byte[] localAddress) throws UnknownHostException {
        if (localAddress.length == 0) {
            // 空地址，默认使用IPv4回环地址
            return "127.0.0.1";
        }

        byte[] ipBytes = normalizeAddress(localAddress);
        var inetAddress = InetAddress.getByAddress(ipBytes);

        if (inetAddress instanceof Inet4Address inet4Address) {
            if (inet4Address.isAnyLocalAddress()) return "127.0.0.1";
            return inetAddress.getHostAddress();
        } else if (inetAddress instanceof Inet6Address inet6Address) {
            if (inet6Address.isAnyLocalAddress()) return "127.0.0.1"; // 改为IPv4回环地址
            String hostAddress = inet6Address.getHostAddress();
            // 处理IPv6地址的方括号格式
            if (hostAddress.contains(":")) {
                return "[" + hostAddress + "]";
            }
            return hostAddress;
        }
        throw new IllegalStateException("Unreachable code");
    }


    public static byte[] normalizeAddress(byte[] localAddress) {
        if (localAddress.length == 4) {
            // IPv4 地址，直接返回
            return localAddress;
        } else if (localAddress.length <= 16) {
            // IPv6 地址，可能被截断
            byte[] fullAddress = new byte[16];
            System.arraycopy(localAddress, 0, fullAddress, 0, localAddress.length);
            // 剩余字节已经是0，符合IPv6地址补零的要求
            return fullAddress;
        } else {
            throw new IllegalArgumentException("Invalid address length: " + localAddress.length);
        }
    }

    @NotNull
    public static List<IPAddress> remapBanListAddress(@NotNull IPAddress banAddress) {
        banAddress = banAddress.isIPv4Convertible() ? banAddress.toIPv4() : banAddress.toIPv6();
        boolean ipv4RemappingEnabled = Main.getMainConfig().getBoolean("banlist-remapping.ipv4.enabled");
        boolean ipv6RemappingEnabled = Main.getMainConfig().getBoolean("banlist-remapping.ipv6.enabled");
        if (banAddress.isIPv4() && ipv4RemappingEnabled) {
            int remapRange = Main.getMainConfig().getInt("banlist-remapping.ipv4.remap-range");
            if (banAddress.getPrefixLength() != null && banAddress.getPrefixLength() <= remapRange)
                return generateRemappedPairIfPossible(banAddress.toPrefixBlock());
            return generateRemappedPairIfPossible(banAddress.toPrefixBlock(remapRange));
        }
        if (banAddress.isIPv6() && ipv6RemappingEnabled) {
            int remapRange = Main.getMainConfig().getInt("banlist-remapping.ipv6.remap-range");
            if (banAddress.getPrefixLength() != null && banAddress.getPrefixLength() <= remapRange)
                return generateRemappedPairIfPossible(banAddress.toPrefixBlock());
            return generateRemappedPairIfPossible(banAddress.toPrefixBlock(remapRange));
        }
        return List.of(banAddress);
    }

    private static List<IPAddress> generateRemappedPairIfPossible(IPAddress address) {
        List<IPAddress> addrs = new ArrayList<>(2);
        addrs.add(address);
        Integer prefixLength = address.getPrefixLength();
        IPAddress body = address.withoutPrefixLength();

        if (body.isIPv4()) { // 如果是 IPV4，则为其生成 IPV6 映射地址，并映射 PrefixLength
            IPAddress instance = body.toIPv6();
            if (prefixLength != null) {
                if (prefixLength > 32)
                    throw new IllegalArgumentException("Invalid prefix length for IPv4 address: " + address);
                instance.toPrefixBlock(prefixLength + 96);
            }
            addrs.add(instance);
            return addrs;
        }
        if (body.isIPv6() && body.isIPv4Convertible()) { // 如果是 IPV6 且可以映射 IPV4，则为其生成原始 IPV4 地址，映射 PrefixLength
            IPAddress instance = body.toIPv4();
            if (prefixLength != null) {
                if (prefixLength < 32)
                    throw new IllegalArgumentException("Invalid prefix length for IPv6 address: " + address);
                instance.toPrefixBlock(prefixLength - 96);
            }
            return addrs;
        }
        return addrs;
    }
}
