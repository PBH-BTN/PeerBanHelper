package com.ghostchu.peerbanhelper.util;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.ReloadStatus;
import com.google.common.net.HostAndPort;
import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import inet.ipaddr.ipv4.IPv4Address;
import inet.ipaddr.ipv6.IPv6Address;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
/**
 * IP 地址工具类
 */
public final class IPAddressUtil {
    private static final IPAddress INVALID_ADDRESS_MISSINGNO = new IPAddressString("127.123.123.123").getAddress();
    private static @NotNull List<IPAddress> nat64PrefixList = List.of(new IPAddressString("64:ff9b::/96").getAddress());

    static {
        reload();
    }

    public static ReloadResult reload() {
        var prefixList64 = new ArrayList<IPAddress>();
        for (String s : Main.getMainConfig().getStringList("ip-remapping.nat64.prefix")) {
            try {
                prefixList64.add(new IPAddressString(s).toAddress());
            } catch (Exception e) {
                log.error("Unable to parse NAT64 prefix {}", s, e);
            }
        }
        nat64PrefixList = prefixList64;
        return new ReloadResult(ReloadStatus.SUCCESS, null, null);
    }

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

    @Nullable
    public static IPAddress extractIfNAT64(@NotNull IPAddress ipAddress) {
        if (!Main.getMainConfig().getBoolean("ip-remapping.nat64.enabled", true)) return ipAddress;
        for (var prefix : nat64PrefixList) {
            if (prefix.contains(ipAddress)) {
                return ipAddress.toIPv6().getEmbeddedIPv4Address();
            }
        }
        return null;
    }

    public static boolean isNAT64(@NotNull IPAddress ipAddress) {
        if (!Main.getMainConfig().getBoolean("ip-remapping.nat64.enabled", true)) return false;
        for (var prefix : nat64PrefixList) {
            if (prefix.contains(ipAddress)) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    public static List<IPAddress> remapBanListAddress(@NotNull IPAddress banAddress) {
        return remapBanListAddress(banAddress, true);
    }

    @NotNull
    public static List<IPAddress> remapBanListAddress(@NotNull IPAddress banAddress, boolean supportRangeBan) {
        Set<IPAddress> addresses = new HashSet<>();
        banAddress = banAddress.isIPv4Convertible() ? banAddress.toIPv4() : banAddress.toIPv6();
        IPAddress nat64Extracted = extractIfNAT64(banAddress);
        addresses.add(banAddress);
        addresses.add(nat64Extracted);
        boolean ipv4RemappingEnabled = supportRangeBan && Main.getMainConfig().getBoolean("banlist-remapping.ipv4.enabled");
        boolean ipv6RemappingEnabled = supportRangeBan && Main.getMainConfig().getBoolean("banlist-remapping.ipv6.enabled");
        if (ipv4RemappingEnabled && (banAddress.isIPv4() || nat64Extracted != null)) {
            IPAddress addressToUse = nat64Extracted != null ? nat64Extracted : banAddress;
            int remapRange = Main.getMainConfig().getInt("banlist-remapping.ipv4.remap-range");
            if (addressToUse.getPrefixLength() != null && addressToUse.getPrefixLength() <= remapRange) {
                addresses.addAll(generateRemappedPairIfPossible(addressToUse.toPrefixBlock()));
            } else {
                addresses.addAll(generateRemappedPairIfPossible(addressToUse.toPrefixBlock(remapRange)));
            }
        }
        if (ipv6RemappingEnabled && banAddress.isIPv6() && nat64Extracted == null) { // 排除 NAT64 地址
            int remapRange = Main.getMainConfig().getInt("banlist-remapping.ipv6.remap-range");
            if (banAddress.getPrefixLength() != null && banAddress.getPrefixLength() <= remapRange) {
                addresses.addAll(generateRemappedPairIfPossible(banAddress.toPrefixBlock()));
            } else {
                addresses.addAll(generateRemappedPairIfPossible(banAddress.toPrefixBlock(remapRange)));
            }
        }
        addresses.addAll(generateRemappedPairIfPossible(banAddress));
        return addresses.stream().distinct().toList();
    }

    public static HostAndPort extractTeredo(IPAddress ipAddress) {
        IPv6Address v6 = ipAddress.toIPv6();
        IPAddress clientIpv4Address = new IPv4Address(~v6.getEmbeddedIPv4Address().intValue());
        // update port to teredo port that encoded in teredo address
        int clientOutboundUdpPort = (~v6.getSegment(5).getValue().intValue()) & 0xFFFF;
        return HostAndPort.fromParts(clientIpv4Address.toNormalizedString(), clientOutboundUdpPort);
    }

    private static List<IPAddress> generateRemappedPairIfPossible(IPAddress address) {
        List<IPAddress> addrs = new ArrayList<>(2);
        addrs.add(address);
        if (address.isIPv4()) { // 如果是 IPV4，则为其生成 IPV6 映射地址
            addrs.add(address.toIPv6());
        } else if (address.isIPv6() && address.isIPv4Convertible()) { // 如果是 IPV6 且可以映射 IPV4，则为其生成原始 IPV4 地址
            addrs.add(address.toIPv4());
        } else if (address.isIPv6() && isNAT64(address.toIPv6())  // 如果是 NAT64 地址，则为其生成原始 IPV4 地址
                && Main.getMainConfig().getBoolean("ip-remapping.nat64", true)) {
            addrs.add(address.toIPv6().getEmbeddedIPv4Address());
        } else if (address.isIPv6() && address.toIPv6().isTeredo()) { // 如果是 Teredo，生成原始 IPV4 地址
            addrs.add(getIPAddress(extractTeredo(address).getHost()));
        }
        return addrs;
    }
}
