package com.ghostchu.peerbanhelper.downloader.impl.biglybt;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.downloader.AbstractDownloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderFeatureFlag;
import com.ghostchu.peerbanhelper.downloader.DownloaderLoginResult;
import com.ghostchu.peerbanhelper.downloader.DownloaderStatistics;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.BiglyBTTorrent;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.ConnectorData;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.bean.clientbound.BanBean;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.bean.clientbound.BanListReplacementBean;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.bean.serverbound.MetadataCallbackBean;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper.DownloadRecord;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper.PeerManagerRecord;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper.PeerRecord;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper.StatisticsRecord;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.peer.PeerImpl;
import com.ghostchu.peerbanhelper.peer.PeerMessage;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.torrent.Tracker;
import com.ghostchu.peerbanhelper.torrent.TrackerImpl;
import com.ghostchu.peerbanhelper.util.ByteUtil;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.vdurmont.semver4j.Semver;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class BiglyBT extends AbstractDownloader {
    private final String apiEndpoint;
    private final HttpClient httpClient;
    private final Config config;
    private final String connectorPayload;
    private Semver semver = new Semver("0.0.0");

    public BiglyBT(String name, Config config, AlertManager alertManager) {
        super(name, alertManager);
        this.config = config;
        this.apiEndpoint = config.getEndpoint();
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        Methanol.Builder builder = Methanol
                .newBuilder()
                .version(HttpClient.Version.valueOf(config.getHttpVersion()))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .defaultHeader("Authorization", "Bearer " + config.getToken())
                .defaultHeader("Content-Type", "application/json")
                .connectTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .headersTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .readTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .cookieHandler(cm);
        if (!config.isVerifySsl() && HTTPUtil.getIgnoreSslContext() != null) {
            builder.sslContext(HTTPUtil.getIgnoreSslContext());
        }
        this.httpClient = builder.build();
        this.connectorPayload = JsonUtil.getGson().toJson(new ConnectorData("PeerBanHelper", Main.getMeta().getVersion(), Main.getMeta().getAbbrev()));
    }

    public static BiglyBT loadFromConfig(String name, JsonObject section, AlertManager alertManager) {
        Config config = JsonUtil.getGson().fromJson(section.toString(), Config.class);
        return new BiglyBT(name, config, alertManager);
    }

    public static BiglyBT loadFromConfig(String name, ConfigurationSection section, AlertManager alertManager) {
        Config config = Config.readFromYaml(section);
        return new BiglyBT(name, config, alertManager);
    }

    @Override
    public List<DownloaderFeatureFlag> getFeatureFlags() {
        return List.of(DownloaderFeatureFlag.READ_PEER_PROTOCOLS, DownloaderFeatureFlag.UNBAN_IP);
    }

    @Override
    public JsonObject saveDownloaderJson() {
        return JsonUtil.getGson().toJsonTree(config).getAsJsonObject();
    }

    @Override
    public YamlConfiguration saveDownloader() {
        return config.saveToYaml();
    }

    @Override
    public DownloaderLoginResult login0() {
        HttpResponse<String> resp;
        try {
            resp = httpClient.send(MutableRequest.GET(apiEndpoint + "/metadata"), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                MetadataCallbackBean metadataCallbackBean = JsonUtil.standard().fromJson(resp.body(), MetadataCallbackBean.class);
                // 验证版本号
                var version = metadataCallbackBean.getPluginVersion();
                this.semver = new Semver(version);
                // 检查是否大于等于 1.2.8
                if (this.semver.isLowerThan("1.3.0")) {
                    return new DownloaderLoginResult(DownloaderLoginResult.Status.REQUIRE_TAKE_ACTIONS, new TranslationComponent(Lang.DOWNLOADER_BIGLYBT_INCORRECT_ADAPTER_VERSION, "1.2.9"));
                }
                MutableRequest request = MutableRequest.POST(apiEndpoint + "/setconnector", HttpRequest.BodyPublishers.ofString(connectorPayload));
                try {
                    httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                } catch (Exception ignored) {
                }
                return new DownloaderLoginResult(DownloaderLoginResult.Status.SUCCESS, new TranslationComponent(Lang.STATUS_TEXT_OK));
            }
            if (resp.statusCode() == 403) {
                return new DownloaderLoginResult(DownloaderLoginResult.Status.INCORRECT_CREDENTIAL, new TranslationComponent(Lang.DOWNLOADER_LOGIN_INCORRECT_CRED));
            }
            return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_LOGIN_EXCEPTION, "statusCode=" + resp.statusCode()));
        } catch (Exception e) {
            return new DownloaderLoginResult(DownloaderLoginResult.Status.NETWORK_ERROR, new TranslationComponent(Lang.DOWNLOADER_LOGIN_IO_EXCEPTION, e.getClass().getName() + ": " + e.getMessage()));
        }
    }

    @Override
    public String getEndpoint() {
        return apiEndpoint;
    }

    @Override
    public String getType() {
        return "BiglyBT";
    }

    @Override
    public boolean isPaused() {
        return config.isPaused();
    }

    @Override
    public void setPaused(boolean paused) {
        super.setPaused(paused);
        config.setPaused(paused);
    }

    @Override
    public void setBanList(@NotNull Collection<PeerAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed, boolean applyFullList) {
        if (removed != null && removed.isEmpty() && added != null && config.isIncrementBan() && !applyFullList) {
            setBanListIncrement(added);
        } else {
            setBanListFull(fullList);
        }
    }

    @Override
    public List<Torrent> getTorrents() {
        return fetchTorrents(List.of(BiglyBTDownloadStateConst.ST_DOWNLOADING, BiglyBTDownloadStateConst.ST_SEEDING, BiglyBTDownloadStateConst.ST_ERROR), !config.isIgnorePrivate());
    }

    @Override
    public List<Torrent> getAllTorrents() {
        return fetchTorrents(Collections.emptyList(), true);
    }

    private List<Torrent> fetchTorrents(List<Object> filtersUrlEncoded, boolean includePrivate) {
        HttpResponse<String> request;
        try {
            StringBuilder urlBuilder = new StringBuilder(apiEndpoint + "/downloads");
            if (!filtersUrlEncoded.isEmpty()) {
                urlBuilder.append("?filter=");
                urlBuilder.append(String.join("&filter=", filtersUrlEncoded.stream().map(Object::toString).toArray(String[]::new)));
            }
            request = httpClient.send(MutableRequest.GET(urlBuilder.toString()),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (request.statusCode() != 200) {
            throw new IllegalStateException(tlUI(Lang.DOWNLOADER_BIGLYBT_INCORRECT_RESPONSE, request.statusCode(), request.body()));
        }
        List<DownloadRecord> torrentDetail = JsonUtil.getGson().fromJson(request.body(), new TypeToken<List<DownloadRecord>>() {
        }.getType());
        List<Torrent> torrents = new ArrayList<>();
        for (DownloadRecord detail : torrentDetail) {
            if (!includePrivate && detail.getTorrent().isPrivateTorrent()) {
                continue;
            }
            torrents.add(new BiglyBTTorrent(
                    detail.getTorrent().getInfoHash(),
                    detail.getName(),
                    detail.getTorrent().getInfoHash(),
                    detail.getTorrent().getSize(),
                    detail.getTorrent().getSize() - detail.getStats().getRemainingBytes(), // 种子总大小 减去 (包含未选择文件的)尚未下载大小 等于 已下载内容大小
                    detail.getStats().getCompletedInThousandNotation() / 1000d,
                    detail.getStats().getRtUploadSpeed(),
                    detail.getStats().getRtDownloadSpeed(),
                    detail.getTorrent().isPrivateTorrent(),
                    detail.getTrackers()));
        }
        return torrents;
    }

    @Override
    public List<Tracker> getTrackers(Torrent torrent) {
        BiglyBTTorrent biglyBTTorrent = (BiglyBTTorrent) torrent;
        if (biglyBTTorrent.getTrackers() == null) {
            return Collections.emptyList();
        }
        return biglyBTTorrent.getTrackers().stream().map(t -> (Tracker) new TrackerImpl(t)).toList();
    }

    @Override
    public void setTrackers(Torrent torrent, List<Tracker> trackers) {
        StringBuilder sb = new StringBuilder();
        for (Tracker tracker : trackers) {
            tracker.getTrackersInGroup().forEach(t -> sb.append(t).append("\n"));
            sb.append("\n");
        }
        HttpResponse<String> resp;
        try {
            resp = httpClient.send(
                    MutableRequest.PATCH(apiEndpoint + "/download/" + torrent.getId() + "/trackers",
                            HttpRequest.BodyPublishers.ofString(sb.toString().trim())),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (resp.statusCode() != 200) {
            throw new IllegalStateException(tlUI(Lang.TRACKER_FAILED_TO_UPDATE_TRACKER, torrent.getHash(), resp.statusCode(), resp.body()));
        }
    }

    @Override
    public DownloaderStatistics getStatistics() {
        HttpResponse<String> resp;
        try {
            resp = httpClient.send(MutableRequest.GET(apiEndpoint + "/statistics"),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (resp.statusCode() != 200) {
            throw new IllegalStateException(tlUI(Lang.DOWNLOADER_FAILED_REQUEST_STATISTICS, resp.statusCode(), resp.body()));
        }
        StatisticsRecord statisticsRecord = JsonUtil.getGson().fromJson(resp.body(), StatisticsRecord.class);
        return new DownloaderStatistics(statisticsRecord.getOverallDataBytesSent(), statisticsRecord.getOverallDataBytesReceived());
    }

    @Override
    public List<Peer> getPeers(Torrent torrent) {
        HttpResponse<String> resp;
        try {
            resp = httpClient.send(MutableRequest.GET(apiEndpoint + "/download/" + torrent.getId() + "/peers"),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (resp.statusCode() == 404) { // 种子被删除或者种子错误时会返回 404
            return new ArrayList<>(); // 不能为不可变列表
        }
        if (resp.statusCode() != 200) {
            throw new IllegalStateException(tlUI(Lang.DOWNLOADER_BIGLYBT_FAILED_REQUEST_PEERS_LIST_IN_TORRENT, resp.statusCode(), resp.body()));
        }
        PeerManagerRecord peerManagerRecord = JsonUtil.getGson().fromJson(resp.body(), PeerManagerRecord.class);
        List<Peer> peersList = new ArrayList<>();
        for (PeerRecord peer : peerManagerRecord.getPeers()) {
            var peerId = new String(ByteUtil.hexToByteArray(peer.getPeerId()), StandardCharsets.ISO_8859_1);
            if (peerId.length() > 8) {
                peerId = peerId.substring(0, 8);
            }
            if (peer.getIp() == null || peer.getIp().isBlank()) {
                continue;
            }
            var supportedMessages = new ArrayList<PeerMessage>();
            if (peer.getPeerSupportedMessages() != null) {
                supportedMessages.addAll(peer.getPeerSupportedMessages().stream().map(str -> {
                    try {
                        return PeerMessage.valueOf(str.toUpperCase(Locale.ROOT).replace("-", "_"));
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                }).filter(Objects::nonNull).toList());
            }
            peersList.add(new PeerImpl(
                    new PeerAddress(peer.getIp(), peer.getPort()),
                    peer.getIp(),
                    peerId,
                    peer.getClient(),
                    peer.getStats().getRtDownloadSpeed(),
                    peer.getStats().getTotalReceived(),
                    peer.getStats().getRtUploadSpeed(),
                    peer.getStats().getTotalSent(),
                    peer.getPercentDoneInThousandNotation() / 1000d,
                    null,
                    supportedMessages,
                    peer.getState() != 30 && peer.getState() != 40
            ));
        }
        return peersList;
    }

    private void setBanListIncrement(Collection<BanMetadata> added) {
        BanBean bean = new BanBean(added.stream().map(b -> b.getPeer().getAddress().getIp()).distinct().toList());
        try {
            HttpResponse<String> request = httpClient.send(MutableRequest
                            .POST(apiEndpoint + "/bans", HttpRequest.BodyPublishers.ofString(JsonUtil.getGson().toJson(bean)))
                    , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (request.statusCode() != 200) {
                log.error(tlUI(Lang.DOWNLOADER_BIGLYBT_INCREAMENT_BAN_FAILED, name, apiEndpoint, request.statusCode(), "HTTP ERROR", request.body()));
                throw new IllegalStateException("Save BiglyBT banlist error: statusCode=" + request.statusCode());
            }
        } catch (Exception e) {
            log.error(tlUI(Lang.DOWNLOADER_BIGLYBT_INCREAMENT_BAN_FAILED, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
            throw new IllegalStateException(e);
        }
    }

    private void setBanListFull(Collection<PeerAddress> peerAddresses) {
        BanListReplacementBean bean = new BanListReplacementBean(peerAddresses.stream().map(PeerAddress::getIp).distinct().toList(), false);
        try {
            HttpResponse<String> request = httpClient.send(MutableRequest.newBuilder()
                            .uri(URI.create(apiEndpoint + "/bans"))
                            .method("PUT", HttpRequest.BodyPublishers.ofString(JsonUtil.getGson().toJson(bean)))
                            .build()
                    , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (request.statusCode() != 200) {
                log.error(tlUI(Lang.DOWNLOADER_BIGLYBT_FAILED_SAVE_BANLIST, name, apiEndpoint, request.statusCode(), "HTTP ERROR", request.body()));
                throw new IllegalStateException("Save BiglyBT banlist error: statusCode=" + request.statusCode());
            }
        } catch (Exception e) {
            log.error(tlUI(Lang.DOWNLOADER_BIGLYBT_FAILED_SAVE_BANLIST, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {

    }

    @NoArgsConstructor
    @Data
    public static class Config {

        private String type;
        private String endpoint;
        private String token;
        private String httpVersion;
        private boolean incrementBan;
        private boolean verifySsl;
        private boolean ignorePrivate;
        private boolean paused;

        public static Config readFromYaml(ConfigurationSection section) {
            Config config = new Config();
            config.setType("biglybt");
            config.setEndpoint(section.getString("endpoint"));
            if (config.getEndpoint().endsWith("/")) { // 浏览器复制党 workaround 一下， 避免连不上的情况
                config.setEndpoint(config.getEndpoint().substring(0, config.getEndpoint().length() - 1));
            }
            config.setToken(section.getString("token", ""));
            config.setIncrementBan(section.getBoolean("increment-ban", true));
            config.setHttpVersion(section.getString("http-version", "HTTP_1_1"));
            config.setVerifySsl(section.getBoolean("verify-ssl", true));
            config.setIgnorePrivate(section.getBoolean("ignore-private", false));
            config.setPaused(section.getBoolean("paused", false));
            return config;
        }

        public YamlConfiguration saveToYaml() {
            YamlConfiguration section = new YamlConfiguration();
            section.set("type", "biglybt");
            section.set("endpoint", endpoint);
            section.set("token", token);
            section.set("http-version", httpVersion);
            section.set("increment-ban", incrementBan);
            section.set("ignore-private", ignorePrivate);
            section.set("verify-ssl", verifySsl);
            section.set("paused", paused);
            return section;
        }
    }
}
