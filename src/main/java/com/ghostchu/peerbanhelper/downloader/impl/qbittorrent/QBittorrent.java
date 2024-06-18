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
import com.google.gson.reflect.TypeToken;
import inet.ipaddr.IPAddress;
import lombok.Getter;
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
    @Getter
    private final String webuiEndpoint;
    private final String username;
    private final String password;
    private final String name;
    private final HttpClient httpClient;
    private final boolean incrementBan;
    private final String baUser;
    private final String baPass;
    private final HttpClient.Version httpVersion;
    private final boolean verifySSL;
    private DownloaderLastStatus lastStatus = DownloaderLastStatus.UNKNOWN;

    public QBittorrent(String name, String webuiEndpoint, String username, String password, String baUser, String baPass, boolean verifySSL, HttpClient.Version httpVersion, boolean incrementBan) {
        this.name = name;
        this.webuiEndpoint = webuiEndpoint;
        this.apiEndpoint = webuiEndpoint + "/api/v2";
        this.incrementBan = incrementBan;
        this.baUser = baUser;
        this.baPass = baPass;
        this.httpVersion = httpVersion;
        this.verifySSL = verifySSL;
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        Methanol.Builder builder = Methanol
                .newBuilder()
                .version(httpVersion)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .userAgent(Main.getUserAgent())
                .connectTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .headersTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .readTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .requestTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .authenticator(new Authenticator() {
                    @Override
                    public PasswordAuthentication requestPasswordAuthenticationInstance(String host, InetAddress addr, int port, String protocol, String prompt, String scheme, URL url, RequestorType reqType) {
                        return new PasswordAuthentication(baUser, baPass.toCharArray());
                    }
                })
                .cookieHandler(cm);
        if (!verifySSL && HTTPUtil.getIgnoreSslContext() != null) {
            builder.sslContext(HTTPUtil.getIgnoreSslContext());
        }
        this.httpClient = builder.build();
        this.username = username;
        this.password = password;
    }

    public static QBittorrent loadFromConfig(String name, JsonObject section) {
        String webuiEndpoint = section.get("endpoint").getAsString();
        if (webuiEndpoint.endsWith("/")) { // 浏览器复制党 workaround 一下， 避免连不上的情况
            webuiEndpoint = webuiEndpoint.substring(0, webuiEndpoint.length() - 1);
        }
        String username = section.get("username").getAsString();
        String password = section.get("password").getAsString();
        JsonObject basicAuth = section.getAsJsonObject("basic-auth");
        String baUser = basicAuth.get("user").getAsString();
        String baPass = basicAuth.get("pass").getAsString();
        String httpVersion = section.get("http-version").getAsString();
        boolean incrementBan = section.get("increment-ban").getAsBoolean();
        boolean verifySSL = section.get("verify-ssl").getAsBoolean();
        HttpClient.Version httpVersionEnum;
        try {
            httpVersionEnum = HttpClient.Version.valueOf(httpVersion);
        } catch (IllegalArgumentException e) {
            httpVersionEnum = HttpClient.Version.HTTP_1_1;
        }
        return new QBittorrent(name, webuiEndpoint, username, password, baUser, baPass, verifySSL, httpVersionEnum, incrementBan);
    }

    public static QBittorrent loadFromConfig(String name, ConfigurationSection section) {
        String webuiEndpoint = section.getString("endpoint");
        if (webuiEndpoint.endsWith("/")) { // 浏览器复制党 workaround 一下， 避免连不上的情况
            webuiEndpoint = webuiEndpoint.substring(0, webuiEndpoint.length() - 1);
        }
        String username = section.getString("username");
        String password = section.getString("password");
        String baUser = section.getString("basic-auth.user");
        String baPass = section.getString("basic-auth.pass");
        String httpVersion = section.getString("http-version", "HTTP_1_1");
        boolean incrementBan = section.getBoolean("increment-ban");
        boolean verifySSL = section.getBoolean("verify-ssl", true);
        HttpClient.Version httpVersionEnum;
        try {
            httpVersionEnum = HttpClient.Version.valueOf(httpVersion);
        } catch (IllegalArgumentException e) {
            httpVersionEnum = HttpClient.Version.HTTP_1_1;
        }

        return new QBittorrent(name, webuiEndpoint, username, password, baUser, baPass, verifySSL, httpVersionEnum, incrementBan);
    }

    @Override
    public JsonObject saveDownloaderJson() {
        JsonObject section = new JsonObject();
        section.addProperty("type", "qbittorrent");
        section.addProperty("endpoint", webuiEndpoint);
        section.addProperty("username", username);
        section.addProperty("password", password);
        section.addProperty("basic-auth.user", baUser);
        section.addProperty("basic-auth.pass", baPass);
        section.addProperty("http-version", httpVersion.name());
        section.addProperty("increment-ban", incrementBan);
        section.addProperty("verify-ssl", verifySSL);
        return section;
    }

    @Override
    public YamlConfiguration saveDownloader() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("type", "qbittorrent");
        section.set("endpoint", webuiEndpoint);
        section.set("username", username);
        section.set("password", password);
        section.set("basic-auth.user", baUser);
        section.set("basic-auth.pass", baPass);
        section.set("http-version", httpVersion.name());
        section.set("increment-ban", incrementBan);
        section.set("verify-ssl", verifySSL);
        return section;
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

    public boolean login() {
        if (isLoggedIn()) return true; // 重用 Session 会话
        try {
            HttpResponse<String> request = httpClient
                    .send(MutableRequest.POST(apiEndpoint + "/auth/login",
                                            FormBodyPublisher.newBuilder()
                                                    .query("username", username)
                                                    .query("password", password).build())
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


    @Override
    public void setBanList(@NotNull Collection<PeerAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed) {
        if (removed != null && removed.isEmpty() && added != null && incrementBan) {
            setBanListIncrement(added);
        } else {
            setBanListFull(fullList);
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
