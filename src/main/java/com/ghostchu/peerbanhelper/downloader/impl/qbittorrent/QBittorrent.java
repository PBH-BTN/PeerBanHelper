package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent;

import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.torrent.TorrentImpl;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import kong.unirest.HttpRequest;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.util.*;

public class QBittorrent implements Downloader {
    // dynamicTable.js -> applyFilter -> active
    private static final List<String> ACTIVE_STATE_LIST = ImmutableList.of(
            "stalledDL",
            "metaDL",
            "forcedMetaDL",
            "downloading",
            "forcedDL",
            "uploading",
            "forcedUP"
    );
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(QBittorrent.class);
    private final String endpoint;
    private final String username;
    private final String password;
    private final UnirestInstance unirest;
    private final String name;
    private final String baUser;
    private final String baPass;

    public QBittorrent(String name, String endpoint, String username, String password, String baUser, String baPass) {
        this.name = name;
        this.unirest = Unirest.spawnInstance();
        this.endpoint = endpoint + "/api/v2";
        this.username = username;
        this.password = password;
        this.baUser = baUser;
        this.baPass = baPass;
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
        HttpRequest<?> body = unirest.post(endpoint + "/auth/login")
                .field("username", username)
                .field("password", password);
        if (StringUtils.isNotEmpty(baUser) || StringUtils.isNotEmpty(baPass)) {
            body = body.basicAuth(baPass, baPass);
        }
        HttpResponse<String> resp = body.asString();
        if (!resp.isSuccess()) {
            log.warn(Lang.DOWNLOADER_QB_LOGIN_FAILED, name, resp.getStatus(), resp.getStatusText(), resp.getBody());
        }
        return resp.isSuccess();
    }

    @Override
    public List<Torrent> getTorrents() {
        HttpResponse<String> resp = unirest.get(endpoint + "/torrents/info").asString();
        if (!resp.isSuccess()) {
            throw new IllegalStateException(String.format(Lang.DOWNLOADER_QB_FAILED_REQUEST_TORRENT_LIST, resp.getStatus(), resp.getBody()));
        }
        List<TorrentDetail> torrentDetail = JsonUtil.getGson().fromJson(resp.getBody(), new TypeToken<List<TorrentDetail>>() {
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
        HttpResponse<String> resp = unirest.get(endpoint + "/sync/torrentPeers?hash=" + torrent.getId()).asString();
        if (!resp.isSuccess()) {
            throw new IllegalStateException(String.format(Lang.DOWNLOADER_QB_FAILED_REQUEST_PEERS_LIST_IN_TORRENT, resp.getStatus(), resp.getBody()));
        }
        System.out.println(resp.getBody());
        JsonObject object = JsonParser.parseString(resp.getBody()).getAsJsonObject();
        JsonObject peers = object.getAsJsonObject("peers");
        List<Peer> peersList = new ArrayList<>();
        for (String s : peers.keySet()) {
            JsonObject singlePeerObject = peers.getAsJsonObject(s);
            SingleTorrentPeer singleTorrentPeer = JsonUtil.getGson().fromJson(singlePeerObject.toString(), SingleTorrentPeer.class);
            System.out.println(singleTorrentPeer.toString());
            peersList.add(singleTorrentPeer);
        }
        return peersList;
    }

    @Override
    public List<PeerAddress> getBanList() {
        HttpResponse<String> json = unirest.get(endpoint + "/app/preferences")
                .asString();
        if (!json.isSuccess()) {
            throw new IllegalStateException(String.format(Lang.DOWNLOADER_QB_API_PREFERENCES_ERR, json.getStatus(), json.getBody()));
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
            log.warn(Lang.DOWNLOADER_QB_FAILED_SAVE_BANLIST, name, endpoint, resp.getStatus(), resp.getStatusText(), resp.getBody());
        }
    }

    @Override
    public void close() throws Exception {
        unirest.close();
    }
}
