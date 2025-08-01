package com.ghostchu.peerbanhelper.util;

import com.ghostchu.peerbanhelper.Main;
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
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request.Builder requestBuilder = original.newBuilder()
                            .header("User-Agent", "PeerBanHelper-DownloaderDiscovery/1.0 " + Main.getUserAgent());
                    return chain.proceed(requestBuilder.build());
                })
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
                            String inetAddress = IPAddressUtil.adaptIP(listenConnection.getLocalAddress());
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
