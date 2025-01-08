package com.ghostchu.peerbanhelper.downloader.impl.biglybt;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.downloader.AbstractDownloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderFeatureFlag;
import com.ghostchu.peerbanhelper.downloader.DownloaderLoginResult;
import com.ghostchu.peerbanhelper.downloader.DownloaderStatistics;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.ConnectorData;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.bean.clientbound.BanBean;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.bean.clientbound.BanListReplacementBean;
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
import com.ghostchu.peerbanhelper.torrent.TorrentImpl;
import com.ghostchu.peerbanhelper.util.ByteUtil;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

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

public class BiglyBT extends AbstractDownloader {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(BiglyBT.class);
    private final String apiEndpoint;
    private final HttpClient httpClient;
    private final Config config;
    private final String connectorPayload;

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
        return List.of(DownloaderFeatureFlag.READ_PEER_PROTOCOLS);
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
        HttpResponse<Void> resp;
        try {
            resp = httpClient.send(MutableRequest.GET(apiEndpoint + "/metadata"), HttpResponse.BodyHandlers.discarding());
            if (resp.statusCode() == 200) {
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

    /**
     * Returns the type of the downloader as a string.
     *
     * @return A constant string identifier "BiglyBT" representing this specific downloader implementation.
     */
    @Override
    public String getType() {
        return "BiglyBT";
    }

    /**
     * Checks whether the BiglyBT downloader is currently paused.
     *
     * @return {@code true} if the downloader is paused, {@code false} otherwise
     */
    @Override
    public boolean isPaused() {
        return config.isPaused();
    }

    /**
     * Sets the paused state of the BiglyBT downloader.
     *
     * Updates the paused status in both the parent abstract downloader and the local configuration.
     * This method ensures that the paused state is consistently maintained across the downloader's
     * configuration and parent class.
     *
     * @param paused A boolean indicating whether the downloader should be paused (true) or active (false)
     */
    @Override
    public void setPaused(boolean paused) {
        super.setPaused(paused);
        config.setPaused(paused);
    }

    /**
     * Sets the ban list for the BiglyBT downloader, either incrementally or by full replacement.
     *
     * @param fullList A complete collection of {@link PeerAddress} to potentially replace the existing ban list
     * @param added A collection of {@link BanMetadata} representing newly added bans
     * @param removed A collection of {@link BanMetadata} representing bans to be removed
     * @param applyFullList A flag indicating whether to apply the full list of peer addresses
     *
     * @implNote This method determines the ban list update strategy based on the following conditions:
     * - If incremental banning is enabled in the configuration
     * - If no bans are being removed
     * - If new bans are present
     * - If not explicitly applying the full list
     * Then it will perform an incremental ban update, otherwise it replaces the entire ban list
     */
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
        HttpResponse<String> request;
        try {
            request = httpClient.send(MutableRequest.GET(apiEndpoint + "/downloads?filter="
                                                         + BiglyBTDownloadStateConst.ST_DOWNLOADING
                                                         + "&filter=" + BiglyBTDownloadStateConst.ST_SEEDING
                                                         + "&filter=" + BiglyBTDownloadStateConst.ST_ERROR),
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
            if (config.isIgnorePrivate() && detail.getTorrent().isPrivateTorrent()) {
                continue;
            }
            torrents.add(new TorrentImpl(
                    detail.getTorrent().getInfoHash(),
                    detail.getName(),
                    detail.getTorrent().getInfoHash(),
                    detail.getTorrent().getSize(),
                    detail.getTorrent().getSize() - detail.getStats().getRemainingBytes(), // 种子总大小 减去 (包含未选择文件的)尚未下载大小 等于 已下载内容大小
                    detail.getStats().getCompletedInThousandNotation() / 1000d,
                    detail.getStats().getRtUploadSpeed(),
                    detail.getStats().getRtDownloadSpeed(),
                    detail.getTorrent().isPrivateTorrent()));
        }
        return torrents;
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

    /**
     * Retrieves a list of peers for a specific torrent from the BiglyBT API.
     *
     * @param torrent The torrent for which to fetch peer information
     * @return A list of {@code Peer} objects representing the peers connected to the torrent
     * @throws IllegalStateException If there is an error communicating with the API or an unexpected response is received
     *
     * This method sends a GET request to the BiglyBT API endpoint to fetch peer details for a given torrent.
     * It handles various scenarios such as:
     * - Torrent not found (returns an empty list)
     * - Successful peer retrieval
     * - Parsing peer information including IP, port, client, download/upload speeds, and supported messages
     *
     * Filters out peers with invalid or blank IP addresses and transforms peer data into {@code PeerImpl} objects.
     */
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
        BanBean bean = new BanBean(added.stream().map(b -> b.getPeer().getAddress().getIp()).toList());
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
        BanListReplacementBean bean = new BanListReplacementBean(peerAddresses.stream().map(PeerAddress::getIp).toList(), false);
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

        /**
         * Reads BiglyBT downloader configuration from a YAML configuration section.
         *
         * @param section The configuration section containing BiglyBT downloader settings
         * @return A configured {@code Config} object for BiglyBT downloader
         *
         * @throws IllegalArgumentException if the endpoint is invalid
         *
         * @see Config
         */
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

        /**
         * Serializes the BiglyBT configuration to a YAML configuration section.
         *
         * @return A YamlConfiguration containing the current configuration settings
         * @see YamlConfiguration
         */
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
