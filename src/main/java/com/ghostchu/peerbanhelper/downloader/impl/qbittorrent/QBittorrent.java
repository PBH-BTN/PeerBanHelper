package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.torrent.TorrentImpl;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
import com.github.mizosoft.methanol.FormBodyPublisher;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import inet.ipaddr.IPAddress;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class QBittorrent implements Downloader {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(QBittorrent.class);
    private final String apiEndpoint;
    private final HttpClient httpClient;
    private final Config config;
    private DownloaderLastStatus lastStatus = DownloaderLastStatus.UNKNOWN;
    private String name;

    public QBittorrent(String name, Config config) {
        this.name = name;
        this.config = config;
        this.apiEndpoint = config.getEndpoint() + "/api/v2";
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        Methanol.Builder builder = Methanol
                .newBuilder()
                .version(HttpClient.Version.valueOf(config.getHttpversion()))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .userAgent(Main.getUserAgent())
                .connectTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .headersTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .readTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .requestTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .authenticator(new Authenticator() {
                    @Override
                    public PasswordAuthentication requestPasswordAuthenticationInstance(String host, InetAddress addr, int port, String protocol, String prompt, String scheme, URL url, RequestorType reqType) {
                        return new PasswordAuthentication(config.getBasicauth().getUser(), config.getBasicauth().getPass().toCharArray());
                    }
                })
                .cookieHandler(cm);
        if (!config.getVerifyssl() && HTTPUtil.getIgnoreSslContext() != null) {
            builder.sslContext(HTTPUtil.getIgnoreSslContext());
        }
        this.httpClient = builder.build();
    }

    public static QBittorrent loadFromConfig(String name, JsonObject section) {
        Config config = JsonUtil.getGson().fromJson(section.toString(), Config.class);
        return new QBittorrent(name, config);
    }

    public static QBittorrent loadFromConfig(String name, ConfigurationSection section) {
        Config config = Config.readFromYaml(section);
        return new QBittorrent(name, config);
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
        if (isLoggedIn()) return true; // 重用 Session 会话
        try {
            HttpResponse<String> request = httpClient
                    .send(MutableRequest.POST(apiEndpoint + "/auth/login",
                                            FormBodyPublisher.newBuilder()
                                                    .query("username", config.getUsername())
                                                    .query("password", config.getPassword()).build())
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                            , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (request.statusCode() != 200) {
                log.warn(Lang.DOWNLOADER_QB_LOGIN_FAILED, name, request.statusCode(), "HTTP ERROR", request.body());
            }
            return request.statusCode() == 200;
        } catch (Exception e) {
            log.warn(Lang.DOWNLOADER_QB_LOGIN_FAILED, name, "N/A", e.getClass().getName(), e.getMessage());
            return false;
        }
    }

    @Override
    public String getEndpoint() {
        return apiEndpoint;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return "qBittorrent";
    }

    public boolean isLoggedIn() {
        HttpResponse<Void> resp;
        try {
            resp = httpClient.send(MutableRequest.GET(apiEndpoint + "/app/version"), HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            return false;
        }
        return resp.statusCode() == 200;
    }

    @Override
    public void setBanList(@NotNull Collection<PeerAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed) {
        if (removed != null && removed.isEmpty() && added != null && config.getIncrementban()) {
            setBanListIncrement(added);
        } else {
            setBanListFull(fullList);
        }
    }

    @Override
    public List<Torrent> getTorrents() {
        HttpResponse<String> request;
        try {
            request = httpClient.send(MutableRequest.GET(apiEndpoint + "/torrents/info?filter=active"), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (request.statusCode() != 200) {
            throw new IllegalStateException(String.format(Lang.DOWNLOADER_QB_FAILED_REQUEST_TORRENT_LIST, request.statusCode(), request.body()));
        }
        List<TorrentDetail> torrentDetail = JsonUtil.getGson().fromJson(request.body(), new TypeToken<List<TorrentDetail>>() {
        }.getType());
        List<Torrent> torrents = new ArrayList<>();
        for (TorrentDetail detail : torrentDetail) {
            torrents.add(new TorrentImpl(detail.getHash(), detail.getName(), detail.getHash(), detail.getTotalSize(), detail.getProgress(), detail.getUpspeed(), detail.getDlspeed()));
        }
        return torrents;
    }

    @Override
    public void relaunchTorrentIfNeeded(Collection<Torrent> torrents) {
        // QB 很棒，什么都不需要做
    }

    @Override
    public void relaunchTorrentIfNeededByTorrentWrapper(Collection<TorrentWrapper> torrents) {
        // QB 很棒，什么都不需要做
    }

    @Override
    public List<Peer> getPeers(Torrent torrent) {
        HttpResponse<String> resp;
        try {
            resp = httpClient.send(MutableRequest.GET(apiEndpoint + "/sync/torrentPeers?hash=" + torrent.getId()),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (resp.statusCode() != 200) {
            throw new IllegalStateException(String.format(Lang.DOWNLOADER_QB_FAILED_REQUEST_PEERS_LIST_IN_TORRENT, resp.statusCode(), resp.body()));
        }

        JsonObject object = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonObject peers = object.getAsJsonObject("peers");
        List<Peer> peersList = new ArrayList<>();
        for (String s : peers.keySet()) {
            JsonObject singlePeerObject = peers.getAsJsonObject(s);
            SingleTorrentPeer singleTorrentPeer = JsonUtil.getGson().fromJson(singlePeerObject.toString(), SingleTorrentPeer.class);
            peersList.add(singleTorrentPeer);
        }
        return peersList;
    }

    @Override
    public List<PeerAddress> getBanList() {
        HttpResponse<String> resp;
        try {
            resp = httpClient.send(MutableRequest.GET(apiEndpoint + "/app/preferences"),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (resp.statusCode() != 200) {
            throw new IllegalStateException(String.format(Lang.DOWNLOADER_QB_API_PREFERENCES_ERR, resp.statusCode(), resp.body()));
        }
        Preferences preferences = JsonUtil.getGson().fromJson(resp.body(), Preferences.class);
        String[] ips = preferences.getBannedIps().split("\n");
        return Arrays.stream(ips).map(ip -> new PeerAddress(ip, 0)).toList();
    }

    @NoArgsConstructor
    @Data
    public static class Config {

        @SerializedName("type")
        private String type;
        @SerializedName("endpoint")
        private String endpoint;
        @SerializedName("username")
        private String username;
        @SerializedName("password")
        private String password;
        @SerializedName("basic-auth")
        private BasicauthDTO basicauth;
        @SerializedName("http-version")
        private String httpversion;
        @SerializedName("increment-ban")
        private Boolean incrementban;
        @SerializedName("verify-ssl")
        private Boolean verifyssl;

        public static Config readFromYaml(ConfigurationSection section) {
            Config config = new Config();
            config.setType("qbittorrent");
            config.setEndpoint(section.getString("endpoint"));
            if (config.getEndpoint().endsWith("/")) { // 浏览器复制党 workaround 一下， 避免连不上的情况
                config.setEndpoint(config.getEndpoint().substring(0, config.getEndpoint().length() - 1));
            }
            config.setUsername(section.getString("username"));
            config.setPassword(section.getString("password"));
            Config.BasicauthDTO basicauthDTO = new BasicauthDTO();
            basicauthDTO.setUser(section.getString("basic-auth.user"));
            basicauthDTO.setPass(section.getString("basic-auth.pass"));
            config.setBasicauth(basicauthDTO);
            config.setHttpversion(section.getString("http-version", "HTTP_1_1"));
            config.setIncrementban(section.getBoolean("increment-ban"));
            config.setVerifyssl(section.getBoolean("verify-ssl", true));
            return config;
        }

        public YamlConfiguration saveToYaml() {
            YamlConfiguration section = new YamlConfiguration();
            section.set("type", "qbittorrent");
            section.set("endpoint", endpoint);
            section.set("username", username);
            section.set("password", password);
            section.set("basic-auth.user", Objects.requireNonNullElse(basicauth.user, ""));
            section.set("basic-auth.pass", Objects.requireNonNullElse(basicauth.pass, ""));
            section.set("http-version", httpversion);
            section.set("increment-ban", incrementban);
            section.set("verify-ssl", verifyssl);
            return section;
        }

        @NoArgsConstructor
        @Data
        public static class BasicauthDTO {
            @SerializedName("user")
            private String user;
            @SerializedName("pass")
            private String pass;
        }
    }

    private void setBanListIncrement(Collection<BanMetadata> added) {
        Map<String, StringJoiner> banTasks = new HashMap<>();
        added.forEach(p -> {
            StringJoiner joiner = banTasks.getOrDefault(p.getTorrent().getHash(), new StringJoiner("|"));
            IPAddress ipAddress = IPAddressUtil.getIPAddress(p.getPeer().getAddress().getIp());
            if (ipAddress.isIPv6()) {
                joiner.add("[" + p.getPeer().getAddress().getIp() + "]" + ":" + p.getPeer().getAddress().getPort());
            } else {
                joiner.add(p.getPeer().getAddress().getIp() + ":" + p.getPeer().getAddress().getPort());
            }
            banTasks.put(p.getTorrent().getHash(), joiner);
        });
        banTasks.forEach((hash, peers) -> {
            try {
                HttpResponse<String> request = httpClient.send(MutableRequest
                                .POST(apiEndpoint + "/transfer/banPeers", FormBodyPublisher.newBuilder()
                                        .query("hash", hash)
                                        .query("peers", peers.toString()).build())
                                .header("Content-Type", "application/x-www-form-urlencoded")
                        , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (request.statusCode() != 200) {
                    log.warn(Lang.DOWNLOADER_QB_INCREAMENT_BAN_FAILED, name, apiEndpoint, request.statusCode(), "HTTP ERROR", request.body());
                    throw new IllegalStateException("Save qBittorrent banlist error: statusCode=" + request.statusCode());
                }
            } catch (Exception e) {
                log.warn(Lang.DOWNLOADER_QB_INCREAMENT_BAN_FAILED, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage(), e);
                throw new IllegalStateException(e);
            }
        });
    }

    private void setBanListFull(Collection<PeerAddress> peerAddresses) {
        StringJoiner joiner = new StringJoiner("\n");
        peerAddresses.forEach(p -> joiner.add(p.getIp()));
        try {
            HttpResponse<String> request = httpClient.send(MutableRequest
                            .POST(apiEndpoint + "/app/setPreferences", FormBodyPublisher.newBuilder()
                                    .query("json", JsonUtil.getGson().toJson(Map.of("banned_IPs", joiner.toString()))).build())
                            .header("Content-Type", "application/x-www-form-urlencoded")
                    , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (request.statusCode() != 200) {
                log.warn(Lang.DOWNLOADER_QB_FAILED_SAVE_BANLIST, name, apiEndpoint, request.statusCode(), "HTTP ERROR", request.body());
                throw new IllegalStateException("Save qBittorrent banlist error: statusCode=" + request.statusCode());
            }
        } catch (Exception e) {
            log.warn(Lang.DOWNLOADER_QB_FAILED_SAVE_BANLIST, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage(), e);
            throw new IllegalStateException(e);
        }
    }

    @Override
    public DownloaderLastStatus getLastStatus() {
        return lastStatus;
    }

    @Override
    public void setLastStatus(DownloaderLastStatus lastStatus) {
        this.lastStatus = lastStatus;
    }

    @Override
    public void close() {

    }
}
