package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.torrent.TorrentImpl;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.util.http.MultiPartBodyPublisher;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;

import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class QBittorrent implements Downloader {
    // dynamicTable.js -> applyFilter -> active
    private static final List<String> ACTIVE_STATE_LIST = ImmutableList.of("stalledDL", "metaDL", "forcedMetaDL", "downloading", "forcedDL", "uploading", "forcedUP");
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(QBittorrent.class);
    private final String endpoint;
    private final String username;
    private final String password;
    private final String name;
    private final HttpClient httpClient;

    public QBittorrent(String name, String endpoint, String username, String password, String baUser, String baPass) {
        this.name = name;
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.httpClient = HttpClient
                .newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .authenticator(new Authenticator() {
            @Override
            public PasswordAuthentication requestPasswordAuthenticationInstance(String host, InetAddress addr, int port, String protocol, String prompt, String scheme, URL url, RequestorType reqType) {
                return new PasswordAuthentication(baUser, baPass.toCharArray());
            }
        })
                .cookieHandler(cm).build();
        this.endpoint = endpoint + "/api/v2";
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

    public boolean login() {
        try {
            java.net.http.HttpResponse request = httpClient.send(java.net.http.HttpRequest.newBuilder(new URI(endpoint + "/auth/login"))
                    .POST(new MultiPartBodyPublisher()
                            .addPart("username", username)
                            .addPart("password", password)
                            .build())
                    .header("User-Agent", Main.getUserAgent()).timeout(Duration.of(30, ChronoUnit.SECONDS)).build(), java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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
        java.net.http.HttpResponse request;
        try {
            request = httpClient.send(java.net.http.HttpRequest.newBuilder(new URI(endpoint + "/torrents/info")).GET().header("User-Agent", Main.getUserAgent()).timeout(Duration.of(30, ChronoUnit.SECONDS)).build(), java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (request.statusCode() != 200) {
            throw new IllegalStateException(String.format(Lang.DOWNLOADER_QB_FAILED_REQUEST_TORRENT_LIST, request.statusCode(), request.body()));
        }
        List<TorrentDetail> torrentDetail = JsonUtil.getGson().fromJson((String) request.body(), new TypeToken<List<TorrentDetail>>() {
        }.getType());
        List<Torrent> torrents = new ArrayList<>();
        for (TorrentDetail detail : torrentDetail) {
            // 过滤下，只要有传输的 Torrent，其它的就不查询了
            if (!ACTIVE_STATE_LIST.contains(detail.getState())) {
                continue;
            }
            torrents.add(new TorrentImpl(detail.getHash(), detail.getName(), detail.getTotalSize()));
        }
        return torrents;
    }

    @Override
    public void relaunchTorrentIfNeeded(Collection<Torrent> torrents) {
        // QB 很棒，什么都不需要做
    }

    @Override
    public List<Peer> getPeers(Torrent torrent) {
        java.net.http.HttpResponse resp;
        try {
            resp = httpClient.send(java.net.http.HttpRequest.newBuilder(new URI(endpoint + "/sync/torrentPeers?hash="+torrent.getId())).GET().header("User-Agent", Main.getUserAgent()).timeout(Duration.of(30, ChronoUnit.SECONDS)).build(), java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (resp.statusCode() != 200) {
            throw new IllegalStateException(String.format(Lang.DOWNLOADER_QB_FAILED_REQUEST_PEERS_LIST_IN_TORRENT, resp.statusCode(), resp.body()));
        }

        JsonObject object = JsonParser.parseString((String) resp.body()).getAsJsonObject();
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
        java.net.http.HttpResponse resp;
        try {
            resp = httpClient.send(java.net.http.HttpRequest.newBuilder(new URI(endpoint + "/app/preferences")).GET().header("User-Agent", Main.getUserAgent()).timeout(Duration.of(30, ChronoUnit.SECONDS)).build(), java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (resp.statusCode() != 200) {
            throw new IllegalStateException(String.format(Lang.DOWNLOADER_QB_API_PREFERENCES_ERR, resp.statusCode(), resp.body()));
        }
        Preferences preferences = JsonUtil.getGson().fromJson((String) resp.body(), Preferences.class);
        String[] ips = preferences.getBannedIps().split("\n");
        return Arrays.stream(ips).map(ip -> new PeerAddress(ip, 0)).toList();
    }


    @Override
    public void setBanList(Collection<PeerAddress> peerAddresses) {
        StringJoiner joiner = new StringJoiner("\n");
        peerAddresses.forEach(p -> joiner.add(p.getIp()));
        try {
            java.net.http.HttpResponse request = httpClient.send(java.net.http.HttpRequest.newBuilder(new URI(endpoint + "/app/setPreferences"))
                    .POST(new MultiPartBodyPublisher()
                            .addPart("json", JsonUtil.getGson().toJson(Map.of("banned_IPs", joiner.toString())))
                            .addPart("password", password)
                            .build())
                    .header("User-Agent", Main.getUserAgent()).timeout(Duration.of(30, ChronoUnit.SECONDS)).build(), java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (request.statusCode() != 200) {
                log.warn(Lang.DOWNLOADER_QB_FAILED_SAVE_BANLIST, name, endpoint, request.statusCode(), "HTTP ERROR", request.body());
            }
        } catch (Exception e) {
            log.warn(Lang.DOWNLOADER_QB_FAILED_SAVE_BANLIST, name, endpoint, "N/A", e.getClass().getName(), e.getMessage());
        }
    }

    @Override
    public void close() throws Exception {

    }
}
