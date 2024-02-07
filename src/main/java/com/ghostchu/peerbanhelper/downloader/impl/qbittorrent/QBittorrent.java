package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent;

import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.torrent.TorrentImpl;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class QBittorrent implements Downloader {
    private final String endpoint;
    private final String username;
    private final String password;
    private final UnirestInstance unirest;
    private final String name;

    public QBittorrent(String name, String endpoint, String username, String password) {
        this.name = name;
        this.unirest = Unirest.spawnInstance();
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
        HttpResponse<String> resp = unirest.post(endpoint + "/auth/login")
                .field("username", username)
                .field("password", password)
                .asString();
        if (!resp.isSuccess()) {
            log.warn("登录到 {} 失败：{} - {}: \n{}", name, resp.getStatus(), resp.getStatusText(), resp.getBody());
        }
        return resp.isSuccess();
    }

    @Override
    public List<Torrent> getTorrents() {
        HttpResponse<String> resp = unirest.get(endpoint + "/torrents/info").asString();
        if (!resp.isSuccess()) {
            throw new IllegalStateException("Failed to request torrent list");
        }
        List<TorrentDetail> torrentDetail = JsonUtil.getGson().fromJson(resp.getBody(), new TypeToken<List<TorrentDetail>>() {
        }.getType());
        List<Torrent> torrents = new ArrayList<>();
        for (TorrentDetail detail : torrentDetail) {
            // 过滤下，只要有传输的 Torrent，其它的就不查询了
            if (detail.getState().equals("uploading") || detail.getState().equals("downloading")) {
                torrents.add(new TorrentImpl(detail.getHash(), detail.getName(), detail.getTotalSize(), detail.getDownloaded()));
            }
        }
        return torrents;
    }

    @Override
    public List<Peer> getPeers(String torrentId) {
        HttpResponse<String> resp = unirest.get(endpoint + "/sync/torrentPeers?hash=" + torrentId).asString();
        if (!resp.isSuccess()) {
            throw new IllegalStateException("Failed to request peers list for torrent " + torrentId);
        }
        JsonObject object = JsonParser.parseString(resp.getBody()).getAsJsonObject();
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
        HttpResponse<String> json = unirest.get(endpoint + "/app/preferences")
                .asString();
        if (!json.isSuccess()) {
            throw new IllegalStateException("qBittorrent preferences API not respond a 200 code");
        }
        Preferences preferences = JsonUtil.getGson().fromJson(json.getBody(), Preferences.class);
        String[] ips = preferences.getBannedIps().split("\n");
        return Arrays.stream(ips).map(ip -> new PeerAddress(ip, 0)).toList();
    }


    @Override
    public void setBanList(Collection<PeerAddress> peerAddresses) {
        StringJoiner joiner = new StringJoiner("\n");
        peerAddresses.forEach(p -> joiner.add(p.getIp()));
        HttpResponse<String> resp = unirest.post(endpoint + "/app/setPreferences")
                .field("json", JsonUtil.getGson().toJson(Map.of("banned_IPs", joiner.toString())))
                .asString();
        if (!resp.isSuccess()) {
            log.warn("无法保存 {} ({}) 的 Banlist！{} - {}\n{}", name, endpoint, resp.getStatus(), resp.getStatusText(), resp.getBody());
        }
    }

    @Override
    public void close() throws Exception {
        unirest.close();
    }
}
