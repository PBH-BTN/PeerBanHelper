package com.ghostchu.peerbanhelper.downloader.impl.transmission;

import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import cordelia.client.TrClient;
import cordelia.client.TypedResponse;
import cordelia.rpc.*;
import cordelia.rpc.types.Fields;
import cordelia.rpc.types.Status;
import cordelia.rpc.types.TorrentAction;
import lombok.SneakyThrows;
import org.slf4j.Logger;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Transmission implements Downloader {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(Transmission.class);
    private final String name;
    private final String endpoint;
    private final TrClient client;
    private final String blocklistUrl;
    private DownloaderLastStatus lastStatus = DownloaderLastStatus.UNKNOWN;

    /*
        API 受限，实际实现起来意义不大
    */
    public Transmission(String name, String endpoint, String username, String password, String blocklistUrl, boolean verifySSL, HttpClient.Version httpVersion) {
        this.name = name;
        this.client = new TrClient(endpoint + "/transmission/rpc", username, password, verifySSL, httpVersion);
        this.endpoint = endpoint;
        this.blocklistUrl = blocklistUrl;
        log.warn(Lang.DOWNLOADER_TR_MOTD_WARNING);
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
        return "Transmission";
    }

    @Override
    public boolean login() {
        RqSessionGet get = new RqSessionGet();
        TypedResponse<RsSessionGet> resp = client.execute(get); // 执行任意 RPC 操作以刷新 session
        String version = resp.getArgs().getVersion();
        if (version.startsWith("0.") || version.startsWith("1.") || version.startsWith("2.")) {
            throw new IllegalStateException(String.format(Lang.DOWNLOADER_TR_KNOWN_INCOMPATIBILITY, Lang.DOWNLOADER_TR_INCOMPATIBILITY_BANAPI));
        }
        return true;
    }

    @Override
    public List<Torrent> getTorrents() {
        RqTorrentGet torrent = new RqTorrentGet(Fields.ID, Fields.HASH_STRING, Fields.NAME, Fields.PEERS_CONNECTED, Fields.STATUS, Fields.TOTAL_SIZE, Fields.PEERS, Fields.RATE_DOWNLOAD, Fields.RATE_UPLOAD, Fields.PEER_LIMIT);
        TypedResponse<RsTorrentGet> rsp = client.execute(torrent);
        return rsp.getArgs().getTorrents().stream()
                .filter(t -> t.getStatus() == Status.DOWNLOADING || t.getStatus() == Status.SEEDING)
                .map(TRTorrent::new).collect(Collectors.toList());
    }

    @Override
    public List<Peer> getPeers(Torrent torrent) {
        TRTorrent trTorrent = (TRTorrent) torrent;
        return trTorrent.getPeers();
    }

    @Override
    public List<PeerAddress> getBanList() {
        return Collections.emptyList();
    }

    @SneakyThrows(InterruptedException.class)
    @Override
    public void setBanList(Collection<PeerAddress> peerAddresses, Collection<PeerAddress> added, Collection<PeerAddress> removed) {
        RqSessionSet set = RqSessionSet.builder()
                .blocklistUrl(blocklistUrl + "?t=" + System.currentTimeMillis()) // 更改 URL 来确保更改生效
                .blocklistEnabled(true)
                .build();
        TypedResponse<RsSessionGet> sessionSetResp = client.execute(set);
        if (!sessionSetResp.isSuccess()) {
            log.warn(Lang.DOWNLOADER_TR_INCORRECT_BANLIST_API_RESP, sessionSetResp.getResult());
        }
        Thread.sleep(3000); // Transmission 在这里疑似有崩溃问题？
        RqBlockList updateBlockList = new RqBlockList();
        TypedResponse<RsBlockList> updateBlockListResp = client.execute(updateBlockList);
        if (!updateBlockListResp.isSuccess()) {
            log.warn(Lang.DOWNLOADER_TR_INCORRECT_SET_BANLIST_API_RESP);
        } else {
            log.info(Lang.DOWNLOADER_TR_UPDATED_BLOCKLIST, updateBlockListResp.getArgs().getBlockListSize());
        }
    }

    @Override
    public void relaunchTorrentIfNeeded(Collection<Torrent> torrents) {
        if (torrents.isEmpty()) return;
        log.info(Lang.DOWNLOADER_TR_DISCONNECT_PEERS, torrents.size());
        RqTorrent stop = new RqTorrent(TorrentAction.STOP, new ArrayList<>());
        for (Torrent torrent : torrents) {
            stop.add(Long.parseLong(torrent.getId()));
        }
        client.execute(stop);
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
        RqTorrent resume = new RqTorrent(TorrentAction.START, new ArrayList<>());
        for (Torrent torrent : torrents) {
            resume.add(Long.parseLong(torrent.getId()));
        }
        client.execute(resume);

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
        client.shutdown();
    }
}
