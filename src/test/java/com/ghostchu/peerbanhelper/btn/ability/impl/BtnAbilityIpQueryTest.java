package com.ghostchu.peerbanhelper.btn.ability.impl;

import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BtnAbilityIpQueryTest {
    private final BtnNetwork network = mock(BtnNetwork.class);
    private final List<Request> requests = new ArrayList<>();

    @Test
    void unadvertisedEndpointsStayUnavailableForLegacyServers() {
        var ability = new BtnAbilityIpQuery(network, config());

        assertNull(ability.getLookupEndpoint());
        assertNull(ability.getCommentsEndpoint());
        assertNull(ability.getCommentCountsEndpoint());
    }

    @Test
    void lookupAndCommentOperationsUsePowAndEncodeScope() throws IOException {
        var config = config();
        config.addProperty("lookup_endpoint", "http://localhost/ping/queryIp/lookup?route=global");
        config.addProperty("comments_endpoint", "http://localhost/ping/queryIp/comments");
        config.addProperty("pow_captcha", true);
        client(200, "{}");
        var ability = new BtnAbilityIpQuery(network, config);
        String scope = "2001:db8::/48";

        ability.lookup(scope);
        ability.listComments(scope, 2);
        ability.createComment(new BtnAbilityIpQuery.CreateComment(scope, null, "comment"));
        ability.updateComment(42, new BtnAbilityIpQuery.UpdateComment("edited", 1));
        ability.deleteComment(42);

        verify(network, times(5)).gatherAndSolveCaptchaBlocking(any(Request.Builder.class), eq("ip_query"));
        assertEquals(scope, requests.get(0).url().queryParameter("query"));
        assertEquals("global", requests.get(0).url().queryParameter("route"));
        assertEquals(scope, requests.get(1).url().queryParameter("scope"));
        assertEquals("2", requests.get(1).url().queryParameter("page"));
        assertEquals(List.of("GET", "GET", "POST", "PUT", "DELETE"),
                requests.stream().map(Request::method).toList());
        assertEquals("/ping/queryIp/comments/42", requests.getLast().url().encodedPath());
    }

    @Test
    void batchesOnlyCacheMissesAndCachesZeroCounts() throws IOException {
        var config = config();
        config.addProperty("comment_counts_endpoint", "http://localhost/ping/queryIp/commentCounts");
        config.addProperty("comment_counts_pow_captcha", true);
        client(200, """
                {"192.0.2.1":0,"2001:db8::1":3}
                """);
        var ability = new BtnAbilityIpQuery(network, config);

        assertEquals(Map.of("192.0.2.1", 0L), ability.queryCommentCounts(List.of("192.0.2.1")));
        assertEquals(Map.of("192.0.2.1", 0L, "2001:db8::1", 3L),
                ability.queryCommentCounts(List.of("192.0.2.1", "2001:db8::1")));
        assertEquals(Map.of("192.0.2.1", 0L), ability.queryCommentCounts(List.of("192.0.2.1")));

        verify(network, times(2)).gatherAndSolveCaptchaBlocking(any(Request.Builder.class), eq("ip_query"));
        Buffer body = new Buffer();
        requests.getLast().body().writeTo(body);
        assertEquals("[\"2001:db8::1\"]", body.readUtf8());
    }

    @Test
    void rateLimitedBatchesCanBeRetriedAndDoNotPopulateCache() {
        var config = config();
        config.addProperty("comment_counts_endpoint", "http://localhost/ping/queryIp/commentCounts");
        client(429, "{\"message\":\"Slow down\"}");
        var ability = new BtnAbilityIpQuery(network, config);
        var ips = List.of("192.0.2.1");

        var exception = assertThrows(BtnAbilityIpQuery.UpstreamException.class, () -> ability.queryCommentCounts(ips));
        assertEquals(429, exception.getStatus());
        assertThrows(BtnAbilityIpQuery.UpstreamException.class, () -> ability.queryCommentCounts(ips));
        assertEquals(2, requests.size());
        verify(network, never()).gatherAndSolveCaptchaBlocking(any(), any());
    }

    private static JsonObject config() {
        JsonObject config = new JsonObject();
        config.addProperty("endpoint", "http://localhost/ping/queryIp");
        return config;
    }

    private void client(int code, String json) {
        when(network.getHttpClient()).thenReturn(new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    requests.add(chain.request());
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(code)
                            .message("Test")
                            .body(ResponseBody.create(json, null))
                            .build();
                }).build());
    }
}
