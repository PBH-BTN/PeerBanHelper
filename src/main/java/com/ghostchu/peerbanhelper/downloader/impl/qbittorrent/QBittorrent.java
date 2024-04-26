package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.torrent.TorrentImpl;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.github.mizosoft.methanol.FormBodyPublisher;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
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
    private final String endpoint;
    private final String username;
    private final String password;
    private final String name;
    private final HttpClient httpClient;
    private final boolean incrementBan;
    private DownloaderLastStatus lastStatus = DownloaderLastStatus.UNKNOWN;

    public QBittorrent(String name, String endpoint, String username, String password, String baUser, String baPass, boolean verifySSL, HttpClient.Version httpVersion, boolean incrementBan) {
        this.name = name;
        this.endpoint = endpoint + "/api/v2";
        this.incrementBan = incrementBan;
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        Methanol.Builder builder = Methanol
                .newBuilder()
                .version(httpVersion)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .userAgent(Main.getUserAgent())
                .connectTimeout(Duration.of(15, ChronoUnit.SECONDS))
                .headersTimeout(Duration.of(15, ChronoUnit.SECONDS))
                .readTimeout(Duration.of(30, ChronoUnit.SECONDS))
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

    @Override
    public String getEndpoint() {
        return endpoint;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDownloaderName() {
        return "qBittorrent";
    }

    public boolean isLoggedIn() {
        HttpResponse<Void> resp;
        try {
            resp = httpClient.send(MutableRequest.GET(endpoint + "/app/version"), HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            return false;
        }
        return resp.statusCode() == 200;
    }

    public boolean login() {
        if (isLoggedIn()) return true; // 重用 Session 会话
        try {
            HttpResponse<String> request = httpClient
                    .send(MutableRequest.POST(endpoint + "/auth/login",
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
            log.warn(Lang.DOWNLOADER_QB_LOGIN_FAILED, name, "N/A", e.getClass().getName(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public List<Torrent> getTorrents() {
        HttpResponse<String> request;
        try {
            request = httpClient.send(MutableRequest.GET(endpoint + "/torrents/info?filter=active"), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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
            torrents.add(new TorrentImpl(detail.getHash(), detail.getName(), detail.getHash(), detail.getTotalSize(), detail.getProgress()));
        }
        return torrents;
    }

    @Override
    public void relaunchTorrentIfNeeded(Collection<Torrent> torrents) {
        // QB 很棒，什么都不需要做
    }

    @Override
    public void relaunchTorrentIfNeededByTorrentWrapper(Collection<BanMetadata.TorrentWrapper> torrents) {
        // QB 很棒，什么都不需要做
    }

    @Override
    public List<Peer> getPeers(Torrent torrent) {
        HttpResponse<String> resp;
        try {
            resp = httpClient.send(MutableRequest.GET(endpoint + "/sync/torrentPeers?hash=" + torrent.getId()),
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
            resp = httpClient.send(MutableRequest.GET(endpoint + "/app/preferences"),
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
    public void setBanList(@NotNull Collection<PeerAddress> fullList, @Nullable Collection<PeerAddress> added, @Nullable Collection<PeerAddress> removed) {
        if (removed != null && removed.isEmpty() && added != null && incrementBan) {
            setBanListIncrement(added);
        } else {
            setBanListFull(fullList);
        }
    }

    private void setBanListIncrement(Collection<PeerAddress> added) {
        StringJoiner joiner = new StringJoiner("|");
        added.forEach(p -> joiner.add(p.getIp() + ":" + p.getPort()));
        try {
            HttpResponse<String> request = httpClient.send(MutableRequest
                            .POST(endpoint + "/transfer/banPeers", FormBodyPublisher.newBuilder()
                                    .query("peers", joiner.toString()).build())
                            .header("Content-Type", "application/x-www-form-urlencoded")
                    , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (request.statusCode() != 200) {
                log.warn(Lang.DOWNLOADER_QB_INCREAMENT_BAN_FAILED, name, endpoint, request.statusCode(), "HTTP ERROR", request.body());
                throw new IllegalStateException("Save qBittorrent banlist error: statusCode=" + request.statusCode());
            }
        } catch (Exception e) {
            log.warn(Lang.DOWNLOADER_QB_INCREAMENT_BAN_FAILED, name, endpoint, "N/A", e.getClass().getName(), e.getMessage(), e);
            throw new IllegalStateException(e);
        }
    }

    private void setBanListFull(Collection<PeerAddress> peerAddresses) {
        StringJoiner joiner = new StringJoiner("\n");
        peerAddresses.forEach(p -> joiner.add(p.getIp()));
        try {
            HttpResponse<String> request = httpClient.send(MutableRequest
                            .POST(endpoint + "/app/setPreferences", FormBodyPublisher.newBuilder()
                                    .query("json", JsonUtil.getGson().toJson(Map.of("banned_IPs", joiner.toString()))).build())
                            .header("Content-Type", "application/x-www-form-urlencoded")
                    , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (request.statusCode() != 200) {
                log.warn(Lang.DOWNLOADER_QB_FAILED_SAVE_BANLIST, name, endpoint, request.statusCode(), "HTTP ERROR", request.body());
                throw new IllegalStateException("Save qBittorrent banlist error: statusCode=" + request.statusCode());
            }
        } catch (Exception e) {
            log.warn(Lang.DOWNLOADER_QB_FAILED_SAVE_BANLIST, name, endpoint, "N/A", e.getClass().getName(), e.getMessage(), e);
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
