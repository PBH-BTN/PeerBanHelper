package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ability.impl.BtnAbilityIpQuery;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.Javalin;
import io.javalin.json.JsonMapper;
import okhttp3.HttpUrl;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PBHPeerControllerBtnQueryTest {
    @Test
    void localRoutesKeepOpaqueDetailsAndNormalizeCountKeys() throws Exception {
        JavalinWebContainer container = mock(JavalinWebContainer.class);
        BtnNetwork network = mock(BtnNetwork.class);
        BtnAbilityIpQuery ability = mock(BtnAbilityIpQuery.class);
        when(network.getAbilities()).thenReturn(Map.of(BtnAbilityIpQuery.class, ability));
        when(ability.getLookupEndpoint()).thenReturn(HttpUrl.get("http://localhost/lookup"));
        when(ability.getCommentsEndpoint()).thenReturn(HttpUrl.get("http://localhost/comments"));
        when(ability.getCommentCountsEndpoint()).thenReturn(HttpUrl.get("http://localhost/counts"));

        JsonObject details = JsonParser.parseString("""
                {"lookup":{"canonicalCidr":"2001:db8::/48"},"canComment":false}
                """).getAsJsonObject();
        when(ability.lookup("2001:db8::/48")).thenReturn(details);
        when(ability.queryCommentCounts(List.of("2001:db8::1", "192.0.2.1")))
                .thenReturn(Map.of("2001:db8::1", 4L, "192.0.2.1", 0L));

        Javalin app = Javalin.create(config -> config.jsonMapper(new JsonMapper() {
            @Override
            public @NotNull String toJsonString(@NotNull Object value, @NotNull Type type) {
                return JsonUtil.standard().toJson(value, type);
            }

            @Override
            public <T> @NotNull T fromJsonString(@NotNull String json, @NotNull Type type) {
                return JsonUtil.standard().fromJson(json, type);
            }
        }));
        try {
            when(container.routes()).thenReturn(app.unsafe.routes);
            app.unsafe.routes.beforeMatched(ctx -> {
                if (ctx.routeRoles().contains(Role.USER_WRITE) && ctx.header("Test-Write") == null) {
                    throw new io.javalin.http.ForbiddenResponse();
                }
            });
            new PBHPeerController(container, null, null, null, null, null, null, null, null, network).onEnable();
            app.start("127.0.0.1", 0);
            String base = "http://127.0.0.1:" + app.port() + "/api/peer/";
            try (HttpClient client = HttpClient.newHttpClient()) {
                var lookup = send(client, HttpRequest.newBuilder(
                        URI.create(base + "btnLookup?query=2001%3Adb8%3A%3A%2F48")).GET().build());
                assertEquals(200, lookup.statusCode());
                assertFalse(data(lookup).get("canComment").getAsBoolean());
                assertEquals("2001:db8::/48", data(lookup).getAsJsonObject("lookup").get("canonicalCidr").getAsString());

                var counts = send(client, HttpRequest.newBuilder(URI.create(base + "btnCommentCounts"))
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                ["2001:0db8::1","2001:db8::1","192.0.2.1"]
                                """)).build());
                assertEquals(200, counts.statusCode());
                assertEquals(4, data(counts).get("2001:0db8::1").getAsInt());
                assertEquals(4, data(counts).get("2001:db8::1").getAsInt());
                assertEquals(0, data(counts).get("192.0.2.1").getAsInt());

                var rejected = send(client, HttpRequest.newBuilder(URI.create(base + "btnComments/42"))
                        .DELETE().build());
                assertEquals(403, rejected.statusCode());
                var deleted = send(client, HttpRequest.newBuilder(URI.create(base + "btnComments/42"))
                        .header("Test-Write", "true").DELETE().build());
                assertEquals(200, deleted.statusCode());
                verify(ability).deleteComment(42);
            }
        } finally {
            app.stop();
        }
    }

    private static JsonObject data(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("data");
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
