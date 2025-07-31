package com.ghostchu.peerbanhelper.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.software.os.InternetProtocolStats;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DownloaderDiscovery {
    private final OkHttpClient httpClient;

    public DownloaderDiscovery(HTTPUtil hTTPUtil) {
        this.httpClient = hTTPUtil.newBuilder()
                .callTimeout(8, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    public CompletableFuture<List<DiscoveredDownloader>> scan() {
        return CompletableFuture.supplyAsync(() -> {
            List<DiscoveredDownloader> found = Collections.synchronizedList(new ArrayList<>());
            SystemInfo systemInfo = new SystemInfo();
            var listenConnections = systemInfo.getOperatingSystem().getInternetProtocolStats().getConnections()
                    .stream()
                    .filter(conn -> conn.getState() == InternetProtocolStats.TcpState.LISTEN)
                    .toList();
            log.debug("Found {} listen connections: {}", listenConnections.size(), listenConnections);
            Semaphore semaphore = new Semaphore(64);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (InternetProtocolStats.IPConnection listenConnection : listenConnections) {
                    executor.submit(() -> {
                        try {
                            semaphore.acquire();
                            String inetAddress = getIp(listenConnection.getLocalAddress());
                            var scanResult = checkDownloader(inetAddress, listenConnection.getLocalPort(), listenConnection.getowningProcessId());
                            if (scanResult != null) {
                                found.add(scanResult);
                            }
                        } catch (Exception e) {
                            log.debug("Failed to get downloader information from {}:{}", listenConnection.getLocalAddress(), listenConnection.getLocalPort(), e);
                        } finally {
                            semaphore.release();
                        }
                    });
                }
            }
            return found.stream().distinct().toList();
        });
    }

    private String getIp(byte[] localAddress) throws UnknownHostException {
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


    private byte[] normalizeAddress(byte[] localAddress) {
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

    @SuppressWarnings("HttpUrlsUsage")
    @Nullable
    public DiscoveredDownloader checkDownloader(String host, int port, int pid) {
        Request request = new Request.Builder()
                .url("http://" + host + ":" + port)
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            var server = response.header("Server");
            if (server != null) { // check server
                if (server.contains("Transmission")) return new DiscoveredDownloader(host, port, "Transmission", pid);
                if (server.contains("PeerBanHelper-BiglyBT-Adapter"))
                    return new DiscoveredDownloader(host, port, "BiglyBT", pid);
            }
            var auth = response.header("WWW-Authenticate");
            if (auth != null) {
                if (auth.contains("BitComet Remote Login"))
                    return new DiscoveredDownloader(host, port, "BitComet", pid);
            }
            var body = response.body().string();
            if (body.contains("BitComet Remote Access") || body.contains("BitComet WebUI"))
                return new DiscoveredDownloader(host, port, "BitComet", pid);
            if (body.contains("qBittorrent WebUI")) return new DiscoveredDownloader(host, port, "qBittorrent", pid);
            if (port == 7607) {
                log.debug("7607: {}\n{}", response.headers(), body);
            }
            return null;
        } catch (Exception e) {
            log.debug("Failed to check downloader at {}:{} -> {}", host, port, e.getCause() + ":" + e.getMessage());
            return null;
        }
    }


    @AllArgsConstructor
    @Data
    public static class DiscoveredDownloader {
        private String host;
        private int port;
        private String type;
        private int pid;
    }
}
