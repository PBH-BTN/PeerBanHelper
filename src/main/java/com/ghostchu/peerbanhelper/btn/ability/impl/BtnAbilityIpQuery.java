package com.ghostchu.peerbanhelper.btn.ability.impl;

import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ability.AbstractBtnAbility;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.URLUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class BtnAbilityIpQuery extends AbstractBtnAbility {
    private final BtnNetwork btnNetwork;
    private final String endpoint;
    private final boolean powCaptcha;

    public BtnAbilityIpQuery(BtnNetwork btnNetwork, JsonObject ability) {
        this.btnNetwork = btnNetwork;
        this.endpoint = ability.get("endpoint").getAsString();
        this.powCaptcha = ability.has("pow_captcha") && ability.get("pow_captcha").getAsBoolean();
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
        String url = URLUtil.appendUrl(endpoint, Map.of("ip", address));
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

    @Override
    public void unload() {

    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class IpQueryResult {
        @SerializedName("color")
        private String color;
        @SerializedName("labels")
        private List<String> labels = Collections.emptyList();
        @SerializedName("bans")
        private IpQueryResultBans bans;
        @SerializedName("swarms")
        private IpQueryResultSwarms swarms;

        @AllArgsConstructor
        @NoArgsConstructor
        @Data
        public static class IpQueryResultBans {
            @SerializedName("total")
            private long total;
            @SerializedName("records")
            private List<BanHistoryDto> records;
        }

        @AllArgsConstructor
        @NoArgsConstructor
        @Data
        public static class IpQueryResultSwarms {
            @SerializedName("total")
            private long total;
            @SerializedName("records")
            private List<SwarmTrackerDto> records;
            @SerializedName("concurrent_download_torrents_count")
            private long concurrentDownloadTorrentsCount;
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
        private InetAddress peerIp;
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
        private InetAddress peerIp;
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
