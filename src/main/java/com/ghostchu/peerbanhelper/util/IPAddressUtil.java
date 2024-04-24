package com.ghostchu.peerbanhelper.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
public class IPAddressUtil {
    private static final Cache<String, IPAddress> IP_ADDRESS_CACHE = CacheBuilder.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .recordStats()
            .build();

    @Contract("_ -> !null")
    public static IPAddress getIPAddress(String ip) {
        try {
            return IP_ADDRESS_CACHE.get(ip, () -> new IPAddressString(ip).getAddress());
        } catch (ExecutionException e) {
            log.error("Unable to get ipaddress from ip {}", ip, e);
            return null;
        }
    }
}
