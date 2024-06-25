package com.ghostchu.peerbanhelper.downloader.impl.biglybt;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderBasicAuth;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.downloader.WebViewScriptCallback;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.bean.clientbound.BanBean;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.bean.clientbound.BanListReplacementBean;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper.DownloadRecord;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper.PeerManagerRecord;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper.PeerRecord;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.peer.PeerImpl;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.torrent.TorrentImpl;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

public class BiglyBT implements Downloader {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(BiglyBT.class);
    private final String apiEndpoint;
    private final HttpClient httpClient;
    private final Config config;
    private DownloaderLastStatus lastStatus = DownloaderLastStatus.UNKNOWN;
    private String name;
    private String statusMessage;

    public BiglyBT(String name, Config config) {
        this.name = name;
        this.config = config;
        this.apiEndpoint = config.getEndpoint();
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        Methanol.Builder builder = Methanol
                .newBuilder()
                .version(HttpClient.Version.valueOf(config.getHttpVersion()))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .userAgent(Main.getUserAgent())
                .defaultHeader("Authorization", "Bearer " + config.getToken())
                .defaultHeader("Content-Type", "application/json")
                .connectTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .headersTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .readTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .requestTimeout(Duration.of(30, ChronoUnit.SECONDS))

                .cookieHandler(cm);
        if (!config.getVerifySsl() && HTTPUtil.getIgnoreSslContext() != null) {
            builder.sslContext(HTTPUtil.getIgnoreSslContext());
        }
        this.httpClient = builder.build();
    }

    public static BiglyBT loadFromConfig(String name, JsonObject section) {
        Config config = JsonUtil.getGson().fromJson(section.toString(), Config.class);
        return new BiglyBT(name, config);
    }

    public static BiglyBT loadFromConfig(String name, ConfigurationSection section) {
        Config config = Config.readFromYaml(section);
        return new BiglyBT(name, config);
    }

    @Override
    public JsonObject saveDownloaderJson() {
        return JsonUtil.getGson().toJsonTree(config).getAsJsonObject();
    }

    @Override
    public YamlConfiguration saveDownloader() {
        return config.saveToYaml();
    }

    public boolean login() {
        return isLoggedIn();
    }

    @Override
    public String getEndpoint() {
        return apiEndpoint;
    }

    @Override
    public String getWebUIEndpoint() {
        return config.getEndpoint();
    }

    @Override
    public @Nullable DownloaderBasicAuth getDownloaderBasicAuth() {
        return null;
    }

    @Override
    public @Nullable WebViewScriptCallback getWebViewJavaScript() {
        return null;
    }

    @Override
    public boolean isSupportWebview() {
        return false;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return "BiglyBT";
    }

    public boolean isLoggedIn() {
        HttpResponse<Void> resp;
        try {
            resp = httpClient.send(MutableRequest.GET(apiEndpoint + "/metadata"), HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            return false;
        }
        return resp.statusCode() == 200;
    }

    @Override
    public void setBanList(@NotNull Collection<PeerAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed) {
        if (removed != null && removed.isEmpty() && added != null && config.isIncrementBan()) {
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
            throw new IllegalStateException(String.format(Lang.DOWNLOADER_BIGLYBT_INCORRECT_RESPONSE, request.statusCode(), request.body()));
        }
        List<DownloadRecord> torrentDetail = JsonUtil.getGson().fromJson(request.body(), new TypeToken<List<DownloadRecord>>() {
        }.getType());
        List<Torrent> torrents = new ArrayList<>();
        for (DownloadRecord detail : torrentDetail) {
            torrents.add(new TorrentImpl(
                    detail.getTorrent().getHashBase64(),
                    detail.getName(),
                    new String(Base64.getDecoder().decode(detail.getTorrent().getHashBase64()), StandardCharsets.ISO_8859_1),
                    detail.getTorrent().getSize(),
                    detail.getStats().getCompletedInThousandNotation() / 1000d,
                    detail.getStats().getRtUploadSpeed(),
                    detail.getStats().getRtDownloadSpeed()));
        }
        return torrents;
    }

    @Override
    public void relaunchTorrentIfNeeded(Collection<Torrent> torrents) {

    }

    @Override
    public void relaunchTorrentIfNeededByTorrentWrapper(Collection<TorrentWrapper> torrents) {

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
        if (resp.statusCode() != 200) {
            throw new IllegalStateException(String.format(Lang.DOWNLOADER_BIGLYBT_FAILED_REQUEST_PEERS_LIST_IN_TORRENT, resp.statusCode(), resp.body()));
        }
        PeerManagerRecord peerManagerRecord = JsonUtil.getGson().fromJson(resp.body(), PeerManagerRecord.class);
        List<Peer> peersList = new ArrayList<>();
        for (PeerRecord peer : peerManagerRecord.getPeers()) {
            peersList.add(new PeerImpl(
                    new PeerAddress(peer.getIp(), peer.getPort()),
                    new String(Base64.getDecoder().decode(peer.getPeerIdBase64()), StandardCharsets.ISO_8859_1),
                    peer.getClient(),
                    peer.getStats().getRtDownloadSpeed(),
                    peer.getStats().getTotalReceived(),
                    peer.getStats().getRtUploadSpeed(),
                    peer.getStats().getTotalSent(),
                    peer.getPercentDoneInThousandNotation() / 1000d,
                    "unsupported"
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
                log.warn(Lang.DOWNLOADER_BIGLYBT_INCREAMENT_BAN_FAILED, name, apiEndpoint, request.statusCode(), "HTTP ERROR", request.body());
                throw new IllegalStateException("Save BiglyBT banlist error: statusCode=" + request.statusCode());
            }
        } catch (Exception e) {
            log.warn(Lang.DOWNLOADER_BIGLYBT_INCREAMENT_BAN_FAILED, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage(), e);
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
                log.warn(Lang.DOWNLOADER_BIGLYBT_FAILED_SAVE_BANLIST, name, apiEndpoint, request.statusCode(), "HTTP ERROR", request.body());
                throw new IllegalStateException("Save BiglyBT banlist error: statusCode=" + request.statusCode());
            }
        } catch (Exception e) {
            log.warn(Lang.DOWNLOADER_BIGLYBT_FAILED_SAVE_BANLIST, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage(), e);
            throw new IllegalStateException(e);
        }
    }

    @Override
    public DownloaderLastStatus getLastStatus() {
        return lastStatus;
    }

    @Override
    public void setLastStatus(DownloaderLastStatus lastStatus, String statusMessage) {
        this.lastStatus = lastStatus;
        this.statusMessage = statusMessage;
    }

    @Override
    public String getLastStatusMessage() {
        return statusMessage;
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
        private Boolean verifySsl;

        public static Config readFromYaml(ConfigurationSection section) {
            Config config = new Config();
            config.setType("biglybt");
            config.setEndpoint(section.getString("endpoint"));
            if (config.getEndpoint().endsWith("/")) { // 浏览器复制党 workaround 一下， 避免连不上的情况
                config.setEndpoint(config.getEndpoint().substring(0, config.getEndpoint().length() - 1));
            }
            config.setToken(section.getString("token"));
            config.setIncrementBan(section.getBoolean("increment-ban"));
            config.setHttpVersion(section.getString("http-version", "HTTP_1_1"));
            config.setVerifySsl(section.getBoolean("verify-ssl", true));
            return config;
        }

        public YamlConfiguration saveToYaml() {
            YamlConfiguration section = new YamlConfiguration();
            section.set("type", "biglybt");
            section.set("endpoint", endpoint);
            section.set("token", token);
            section.set("http-version", httpVersion);
            section.set("increment-ban", incrementBan);
            section.set("verify-ssl", verifySsl);
            return section;
        }
    }
}
