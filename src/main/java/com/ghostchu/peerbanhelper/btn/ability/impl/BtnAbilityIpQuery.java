package com.ghostchu.peerbanhelper.btn.ability.impl;

import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ability.AbstractBtnAbility;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.URLUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class BtnAbilityIpQuery extends AbstractBtnAbility {
    public static final int MAX_COMMENT_COUNT_BATCH = 50;
    public static final int DEFAULT_COMMENT_PAGE_SIZE = 5;
    private static final MediaType JSON = MediaType.get("application/json");
    private final BtnNetwork btnNetwork;
    private final String endpoint;
    private final boolean powCaptcha;
    private final boolean commentCountsPowCaptcha;
    @Getter
    private final HttpUrl lookupEndpoint;
    @Getter
    private final HttpUrl commentsEndpoint;
    @Getter
    private final HttpUrl commentCountsEndpoint;
    private final Cache<String, Long> commentCountCache = CacheBuilder.newBuilder()
            .maximumSize(4096)
            .expireAfterWrite(Duration.ofMinutes(1))
            .build();
    private final ReentrantLock commentCountRequest = new ReentrantLock();
    @Getter
    private final String iframeEndpoint;

    public BtnAbilityIpQuery(BtnNetwork btnNetwork, JsonObject ability) {
        this.btnNetwork = btnNetwork;
        this.endpoint = ability.get("endpoint").getAsString();
        this.powCaptcha = ability.has("pow_captcha") && ability.get("pow_captcha").getAsBoolean();
        this.lookupEndpoint = optionalUrl(ability, "lookup_endpoint");
        this.commentsEndpoint = optionalUrl(ability, "comments_endpoint");
        this.commentCountsEndpoint = optionalUrl(ability, "comment_counts_endpoint");
        this.commentCountsPowCaptcha = ability.has("comment_counts_pow_captcha")
                && ability.get("comment_counts_pow_captcha").getAsBoolean();
        this.iframeEndpoint = ability.has("iframe_endpoint") ? ability.get("iframe_endpoint").getAsString() : null;
    }

    @Override
    public String getName() {
        return "BtnAbilityIpQuery";
    }

    @Override
    public TranslationComponent getDisplayName() {
        return new TranslationComponent(Lang.BTN_ABILITY_IP_QUERY_TITLE);
    }

    @Override
    public TranslationComponent getDescription() {
        return new TranslationComponent(Lang.BTN_ABILITY_IP_QUERY_DESCRIPTION);
    }

    @Override
    public void load() {
        setLastStatus(true, new TranslationComponent(Lang.BTN_STAND_BY));
    }

    @Nullable
    public IpQueryResult query(@NotNull String address) throws IOException {
        String url = URLUtil.appendUrl(endpoint, Map.of("ip", URLEncoder.encode(address, StandardCharsets.UTF_8)));
        Request.Builder request = new Request.Builder()
                .url(url)
                .get();
        if (powCaptcha) btnNetwork.gatherAndSolveCaptchaBlocking(request, "ip_query");
        try (Response response = btnNetwork.getHttpClient().newCall(request.build()).execute()) {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                log.error(tlUI(Lang.BTN_REQUEST_FAILS, response.code() + " - " + responseBody));
                setLastStatus(false, new TranslationComponent(Lang.BTN_HTTP_ERROR, response.code(), responseBody));
                throw new IOException("Unable perform query: " + response.code() + " - " + responseBody);
            } else {
                IpQueryResult result = JsonUtil.getGson().fromJson(responseBody, IpQueryResult.class);
                setLastStatus(true, new TranslationComponent(Lang.BTN_ABILITY_IP_QUERY_STATUS_OK, address));
                return result;
            }
        }
    }

    private static HttpUrl optionalUrl(JsonObject ability, String key) {
        JsonElement value = ability.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? HttpUrl.parse(value.getAsString())
                : null;
    }

    public JsonObject lookup(String query) throws IOException {
        return requestJson(new Request.Builder()
                .url(lookupEndpoint.newBuilder().addQueryParameter("query", query).build()), powCaptcha);
    }

    public JsonObject listComments(String scope, int page) throws IOException {
        return listComments(scope, page, DEFAULT_COMMENT_PAGE_SIZE);
    }

    public JsonObject listComments(String scope, int page, int pageSize) throws IOException {
        return requestJson(new Request.Builder().url(commentsEndpoint.newBuilder()
                .addQueryParameter("scope", scope)
                .addQueryParameter("page", Integer.toString(page))
                .addQueryParameter("size", Integer.toString(Math.clamp(pageSize, 1, 50))).build()), powCaptcha);
    }

    public JsonObject createComment(CreateComment comment) throws IOException {
        return requestJson(new Request.Builder().url(commentsEndpoint)
                .post(jsonBody(comment)), powCaptcha);
    }

    public JsonObject updateComment(long id, UpdateComment comment) throws IOException {
        return requestJson(new Request.Builder().url(commentUrl(id))
                .put(jsonBody(comment)), powCaptcha);
    }

    public void deleteComment(long id) throws IOException {
        requestJson(new Request.Builder().url(commentUrl(id)).delete(), powCaptcha);
    }

    private HttpUrl commentUrl(long id) {
        return commentsEndpoint.newBuilder().addPathSegment(Long.toString(id)).build();
    }

    public Map<String, Long> queryCommentCounts(List<String> addresses) throws IOException {
        if (addresses.isEmpty() || addresses.size() > MAX_COMMENT_COUNT_BATCH) {
            throw new IllegalArgumentException("Expected between 1 and 50 IP addresses");
        }
        Map<String, Long> counts = new LinkedHashMap<>(commentCountCache.getAllPresent(addresses));
        if (addresses.stream().allMatch(counts::containsKey)) {
            return counts;
        }
        // Limits concurrent upstream batches across browser sessions, including the PoW solver.
        if (!commentCountRequest.tryLock()) {
            throw new UpstreamException(429, null);
        }
        try {
            counts.putAll(commentCountCache.getAllPresent(addresses));
            List<String> missing = addresses.stream().distinct().filter(ip -> !counts.containsKey(ip)).toList();
            if (!missing.isEmpty()) {
                JsonObject result = requestJson(new Request.Builder().url(commentCountsEndpoint)
                        .post(jsonBody(missing)), commentCountsPowCaptcha);
                Map<String, Long> fresh = new LinkedHashMap<>();
                for (String ip : missing) {
                    fresh.put(ip, readCount(result, ip));
                }
                commentCountCache.putAll(fresh);
                counts.putAll(fresh);
            }
            return counts;
        } finally {
            commentCountRequest.unlock();
        }
    }

    private static long readCount(JsonObject result, String ip) throws IOException {
        JsonElement count = result.get(ip);
        if (count != null && count.isJsonPrimitive() && count.getAsJsonPrimitive().isNumber()) {
            try {
                long value = count.getAsBigDecimal().longValueExact();
                if (value >= 0) {
                    return value;
                }
            } catch (ArithmeticException ignored) {
                // Non-integral or out-of-range counts are invalid.
            }
        }
        throw new IOException("Invalid BTN comment count response");
    }

    private RequestBody jsonBody(Object body) {
        return RequestBody.create(JsonUtil.standard().toJson(body), JSON);
    }

    private JsonObject requestJson(Request.Builder builder, boolean requirePow) throws IOException {
        if (requirePow) {
            btnNetwork.gatherAndSolveCaptchaBlocking(builder, "ip_query");
        }
        try (Response response = btnNetwork.getHttpClient().newCall(builder.build()).execute()) {
            if (response.code() == 204) {
                return new JsonObject();
            }
            if (!response.isSuccessful()) {
                String message = null;
                if (response.code() == 400 || response.code() == 409 || response.code() == 429) {
                    try {
                        JsonElement field = JsonParser.parseString(response.body().string())
                                .getAsJsonObject().get("message");
                        if (field != null && field.isJsonPrimitive() && field.getAsJsonPrimitive().isString()) {
                            message = field.getAsString();
                        }
                    } catch (JsonParseException | IllegalStateException ignored) {
                        // Other BTN implementations may return a different error body.
                    }
                }
                throw new UpstreamException(response.code(), message);
            }
            return JsonParser.parseString(response.body().string()).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("Invalid BTN IP query response", e);
        }
    }

    public record CreateComment(String scope, Long parentId, String content) {
    }

    public record UpdateComment(String content, Integer revision) {
    }

    public static final class UpstreamException extends IOException {
        @Getter
        private final int status;

        private UpstreamException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    @Override
    public void unload() {
        commentCountCache.invalidateAll();
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class IpQueryResult {
        @SerializedName("color") // 可以是 red, green, orange, gray，代表风险等级。red = 高危, orange = 中危, green = 低危, gray = 未知
        private String color;
        @SerializedName("labels") // IP 属性标签列表，字符串
        private List<String> labels = Collections.emptyList();
        @SerializedName("bans") // 封禁有关信息
        private IpQueryResultBans bans;
        @SerializedName("swarms") // Swarm 有关信息
        private IpQueryResultSwarms swarms;
        @SerializedName("traffic") // 流量有关信息
        private IpQueryTraffic traffic;
        @SerializedName("torrents") // 种子有关信息
        private IpQueryTorrents torrents;

        @AllArgsConstructor
        @NoArgsConstructor
        @Data
        public static class IpQueryTorrents {
            @SerializedName("duration") // 提供的是最近 duration 时间内的数据，单位是毫秒
            private long duration;
            @SerializedName("count") // 该 IP 访问的种子数量
            private long count;
        }

        @AllArgsConstructor
        @NoArgsConstructor
        @Data
        public static class IpQueryTraffic {
            @SerializedName("duration") // 提供的是最近 duration 时间内的数据，单位是毫秒
            private long duration;
            @SerializedName("to_peer_traffic") // BTN 网络用户上传给该 IP 的总流量，单位是字节，受时间范围约束
            private long toPeerTraffic;
            @SerializedName("from_peer_traffic") // BTN 网络用户从该 IP 下载的总流量，单位是字节，受时间范围约束
            private long fromPeerTraffic;
            @SerializedName("share_ratio") // 该 IP 的分享率，计算方式为 to_peer_traffic / from_peer_traffic，受时间范围约束
            private double shareRatio;
        }

        @AllArgsConstructor
        @NoArgsConstructor
        @Data
        public static class IpQueryResultBans {
            @SerializedName("duration") // 提供的是最近 duration 时间内的数据，单位是毫秒
            private long duration;
            @SerializedName("total") // 该 IP 的封禁记录总数
            private long total;
            @SerializedName("records") // 封禁记录列表，可能很长，注意分页
            private List<BanHistoryDto> records;
        }

        @AllArgsConstructor
        @NoArgsConstructor
        @Data
        public static class IpQueryResultSwarms {
            @SerializedName("duration") // 提供的是最近 duration 时间内的数据，单位是毫秒
            private long duration;
            @SerializedName("total")  // 该 IP 参与的 Swarm 记录总数
            private long total;
            @SerializedName("records") // Swarm 记录列表，可能很长，注意分页
            private List<SwarmTrackerDto> records;
            @SerializedName("concurrent_download_torrents_count") // 该 IP 并发下载的种子数量
            private long concurrentDownloadTorrentsCount;
            @SerializedName("concurrent_seeding_torrents_count") // 该 IP 并发做种的种子数量
            private long concurrentSeedingTorrentsCount;
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class BanHistoryDto {
        @SerializedName("populate_time")
        private Long populateTime;
        @SerializedName("torrent")
        private String torrent;
        @SerializedName("peer_ip")
        private String peerIp;
        @SerializedName("peer_port")
        private Integer peerPort;
        @SerializedName("peer_id")
        private String peerId;
        @SerializedName("peer_client_name")
        private String peerClientName;
        @SerializedName("peer_progress")
        private Double peerProgress;
        @SerializedName("peer_flags")
        private String peerFlags;
        @SerializedName("reporter_progress")
        private Double reporterProgress;
        @SerializedName("to_peer_traffic")
        private Long toPeerTraffic;
        @SerializedName("from_peer_traffic")
        private Long fromPeerTraffic;
        @SerializedName("module_name")
        private String moduleName;
        @SerializedName("rule")
        private String rule;
        @SerializedName("description")
        private String description;
        @SerializedName("structured_data")
        private Map<String, Object> structuredData;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class SwarmTrackerDto {
        @SerializedName("torrent")
        private String torrent;
        @SerializedName("peer_ip")
        private String peerIp;
        @SerializedName("peer_port")
        private Integer peerPort;
        @SerializedName("peer_id")
        private String peerId;
        @SerializedName("peer_client_name")
        private String peerClientName;
        @SerializedName("peer_progress")
        private Double peerProgress;
        @SerializedName("from_peer_traffic")
        private Long fromPeerTraffic;
        @SerializedName("to_peer_traffic")
        private Long toPeerTraffic;
        @SerializedName("from_peer_traffic_offset")
        private Long fromPeerTrafficOffset;
        @SerializedName("to_peer_traffic_offset")
        private Long toPeerTrafficOffset;
        @SerializedName("flags")
        private String flags;
        @SerializedName(value = "first_time_seen")
        private Long firstTimeSeen;
        @SerializedName(value = "last_time_seen")
        private Long lastTimeSeen;
        @SerializedName("user_progress")
        private double userProgress;
    }
}
