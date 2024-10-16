package com.ghostchu.peerbanhelper.util.maven;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

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
        var req = CompletableFuture.supplyAsync(() -> {
            Request request = new Request.Builder().url(urlStr).build();
            long time = System.currentTimeMillis();
            try (Response response = HTTPUtil.getHttpClient(false, null).newCall(request).execute()) {
                if (response.code() != 200) {
                    return Long.MAX_VALUE;
                }
                return System.currentTimeMillis() - time;
            } catch (IOException e) {
                return Long.MAX_VALUE;
            }
        });
        try {
            return req.get(15, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return Long.MAX_VALUE;
        }

    }

    @NotNull
    public static List<MavenCentralMirror> determineBestMirrorServer(Logger logger) {
        logger.info(tlUI(Lang.LIBRARIES_LOADER_DETERMINE_BEST_MIRROR));
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
        logger.info(tlUI(Lang.LIBRARIES_LOADER_DETERMINE_TEST_RESULT));
        list.forEach(e -> {
            String cost = "DNF";
            if (e.getValue() != Long.MAX_VALUE) {
                cost = e.getValue() + "ms";
            }
            logger.info("[" + e.getKey().getRegion() + "] " + e.getKey().name() + ": " + cost);
        });
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
//            System.out.println("Cannot determine the server city: " + e.getClass().getName() + ": " + e.getMessage() + ", falling back to use CN mirror (Did your server behind the GFW, or no internet connection?)");
//        }
//        return inChinaRegion;
//    }

}
