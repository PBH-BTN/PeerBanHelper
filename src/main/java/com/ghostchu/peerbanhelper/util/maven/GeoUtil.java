package com.ghostchu.peerbanhelper.util.maven;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;

public class GeoUtil {
    private static volatile Boolean inChinaRegion = null;


    public static CompletableFuture<Integer> connectTest(String ipAddress, int port, int timeout) {
        return CompletableFuture.supplyAsync(() -> {
            try (Socket socket = new Socket()) {
                long time = System.currentTimeMillis();
                socket.connect(new InetSocketAddress(InetAddress.getByName(ipAddress), port), timeout);
                return (int) (System.currentTimeMillis() - time);
            } catch (IOException ignored) {
                return Integer.MAX_VALUE;
            }
        });
    }

    private static long sendGetTest(String urlStr) {
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.of(5, ChronoUnit.SECONDS))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(urlStr))
                    .timeout(Duration.of(5, ChronoUnit.SECONDS))
                    .GET()
                    .build();
            long time = System.currentTimeMillis();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Long.MAX_VALUE;
            }
            return System.currentTimeMillis() - time;
        } catch (Throwable e) {
            return Long.MAX_VALUE;
        }
    }

    @NotNull
    public static List<MavenCentralMirror> determineBestMirrorServer(Logger logger) {
        List<CompletableFuture<Void>> testEntry = new ArrayList<>();
        Map<MavenCentralMirror, Long> mirrorPingMap = new ConcurrentSkipListMap<>();
        for (MavenCentralMirror value : MavenCentralMirror.values()) {
            testEntry.add(CompletableFuture.supplyAsync(() -> {
                mirrorPingMap.put(value, sendGetTest(value.getTestUrl()));
                return null;
            }));
        }
        testEntry.forEach(CompletableFuture::join);
        List<Map.Entry<MavenCentralMirror, Long>> list = new ArrayList<>(mirrorPingMap.entrySet());
        list.sort(Map.Entry.comparingByValue());
//        logger.info("Maven repository mirror test result:");
//        list.forEach(e -> {
//            String cost = "DNF";
//            if (e.getValue() != Long.MAX_VALUE) {
//                cost = e.getValue() + "ms";
//            }
//            logger.info("[" + e.getKey().getRegion() + "] " + e.getKey().name() + ": " + cost);
//        });
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().filter(e -> e.getValue() != Long.MAX_VALUE).limit(3).map(Map.Entry::getKey).toList();
    }
    // 这个代码是我从我的另一个开源项目复制的，先留着吧，也许以后用得到呢？
//
//    public static boolean inChinaRegion() {
//        // Already know
//        if (inChinaRegion != null) return inChinaRegion;
//        var client = HttpClient.newHttpClient();
//        inChinaRegion = true;
//        var request = HttpRequest.newBuilder()
//                .uri(URI.create("https://cloudflare.com/cdn-cgi/trace"))
//                .timeout(Duration.ofSeconds(7))
//                .build();
//        try {
//            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
//            String[] exploded = response.body().split("\n");
//            for (String s : exploded) {
//                if (s.startsWith("loc=")) {
//                    String[] kv = s.split("=");
//                    if (kv.length != 2) {
//                        continue;
//                    }
//                    String key = kv[0];
//                    String value = kv[1];
//                    if ("loc".equalsIgnoreCase(key) && !"CN".equalsIgnoreCase(value)) {
//                        inChinaRegion = false;
//                        break;
//                    }
//                }
//            }
//        } catch (IOException | InterruptedException e) {
//            System.out.println("Cannot determine the server region: " + e.getClass().getName() + ": " + e.getMessage() + ", falling back to use CN mirror (Did your server behind the GFW, or no internet connection?)");
//        }
//        return inChinaRegion;
//    }

}
