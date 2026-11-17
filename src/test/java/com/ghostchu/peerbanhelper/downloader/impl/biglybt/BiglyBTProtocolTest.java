package com.ghostchu.peerbanhelper.downloader.impl.biglybt;

import com.ghostchu.peerbanhelper.BuildMeta;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.DownloaderLoginResult;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import inet.ipaddr.IPAddressString;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BiglyBTProtocolTest {
    @Test
    void snapshotsAvoidPerTorrentRequestsAndBanStateRecoversAfterRestart() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var instance = new AtomicReference<>("before");
        var revision = new AtomicInteger();
        var fullUpdates = new AtomicInteger();
        var deltas = new AtomicInteger();
        var unexpected = new AtomicInteger();
        var lastFullBody = new AtomicReference<String>();
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            switch (path) {
                case "/metadata" -> reply(exchange, 200, """
                        {"pluginVersion":"2.1.0","protocol":2}
                        """);
                case "/setconnector" -> reply(exchange, 200, "{}");
                case "/v2/snapshot" -> reply(exchange, 200, """
                        [{"download":{"name":"idle","torrent":{"infoHash":"abcdef","size":1},"stats":{}},"peers":[]}]
                        """);
                case "/v2/bans" -> {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    switch (exchange.getRequestMethod()) {
                        case "PUT" -> {
                            lastFullBody.set(body);
                            fullUpdates.incrementAndGet();
                            revision.incrementAndGet();
                        }
                        case "PATCH" -> {
                            if (!body.contains(instance.get())) {
                                reply(exchange, 409, "{}");
                                return;
                            }
                            deltas.incrementAndGet();
                            revision.incrementAndGet();
                        }
                        case "GET" -> { }
                        default -> unexpected.incrementAndGet();
                    }
                    reply(exchange, 200, """
                            {"instance":"%s","revision":%d,"filterEnabled":true}
                            """.formatted(instance.get(), revision.get()));
                }
                default -> {
                    unexpected.incrementAndGet();
                    reply(exchange, 404, "{}");
                }
            }
        });
        server.start();
        BiglyBT adapter = null;
        try (var main = mockStatic(Main.class)) {
            main.when(Main::getMeta).thenReturn(new BuildMeta());
            main.when(Main::getMainConfig).thenReturn(new YamlConfiguration());
            adapter = connect(server);
            assertTrue(adapter.login0().success());
            var torrents = adapter.getTorrents();
            assertEquals(1, torrents.size());
            assertEquals("idle", torrents.getFirst().getName());
            assertTrue(adapter.getPeers(torrents.getFirst()).isEmpty());

            var first = new IPAddressString("198.51.100.1").getAddress();
            var second = new IPAddressString("198.51.100.2").getAddress();
            adapter.setBanList(List.of(first), null, null, true);
            adapter.setBanList(List.of(second), List.of(), List.of(), false);
            assertEquals(1, fullUpdates.get());
            assertEquals(1, deltas.get());

            instance.set("after");
            revision.set(0);
            assertTrue(adapter.login0().success());
            assertEquals(2, fullUpdates.get());
            assertTrue(lastFullBody.get().contains("198.51.100.2"));
            assertFalse(lastFullBody.get().contains("198.51.100.1"));
            assertEquals(0, unexpected.get());
        } finally {
            if (adapter != null) adapter.close();
            server.stop(0);
        }
    }

    @Test
    void legacyMetadataRequiresAnUpgradeBeforeAnyOtherRequest() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var unexpected = new AtomicInteger();
        server.createContext("/", exchange -> {
            if (exchange.getRequestURI().getPath().equals("/metadata")) {
                reply(exchange, 200, """
                        {"pluginVersion":"2.0.3"}
                        """);
            } else {
                unexpected.incrementAndGet();
                reply(exchange, 404, "{}");
            }
        });
        server.start();
        try (var main = mockStatic(Main.class)) {
            main.when(Main::getMeta).thenReturn(new BuildMeta());
            var adapter = connect(server);
            try {
                assertEquals(DownloaderLoginResult.Status.REQUIRE_TAKE_ACTIONS, adapter.login0().status());
                assertEquals(0, unexpected.get());
            } finally {
                adapter.close();
            }
        } finally {
            server.stop(0);
        }
    }

    private static BiglyBT connect(HttpServer server) {
        var config = new BiglyBT.Config();
        config.setName("BiglyBT test");
        config.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
        config.setToken("test");
        config.setVerifySsl(true);
        config.setIncrementBan(true);
        var http = mock(HTTPUtil.class);
        when(http.newBuilderForDownloader()).thenReturn(new OkHttpClient.Builder());
        return new BiglyBT("test", config, null, http, address -> null);
    }

    private static void reply(HttpExchange exchange, int status, String json) throws IOException {
        try (exchange) {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }
    }
}
