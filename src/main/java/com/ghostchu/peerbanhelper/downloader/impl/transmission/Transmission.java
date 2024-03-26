package com.ghostchu.peerbanhelper.downloader.impl.transmission;

import com.ghostchu.peerbanhelper.downloader.Downloader;
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
import org.slf4j.Logger;

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

    /*
        API 受限，实际实现起来意义不大
    */
    public Transmission(String name, String endpoint, String username, String password, String blocklistUrl) {
        this.name = name;
        this.client = new TrClient(endpoint + "/transmission/rpc", username, password);
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
    public boolean login() {
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

    @Override
    public void setBanList(Collection<PeerAddress> peerAddresses) {
        RqSessionSet set = RqSessionSet.builder()
                .blocklistUrl(blocklistUrl)
                .blocklistEnabled(true)
                .build();
        TypedResponse<RsSessionGet> sessionSetResp = client.execute(set);
        if (!sessionSetResp.isSuccess()) {
            log.warn(Lang.DOWNLOADER_TR_INCORRECT_BANLIST_API_RESP, sessionSetResp.getResult());
        }
        RqBlockList updateBlockList = new RqBlockList();
        TypedResponse<RsBlockList> updateBlockListResp = client.execute(updateBlockList);
        if (!updateBlockListResp.isSuccess()) {
            log.warn(Lang.DOWNLOADER_TR_INCORRECT_SET_BANLIST_API_RESP);
        }
    }

    @Override
    public void relaunchTorrentIfNeeded(Collection<Torrent> torrents) {
        log.info(Lang.DOWNLOADER_TR_DISCONNECT_PEERS, torrents.size());

        for (Torrent torrent : torrents) {
            RqTorrent stop = new RqTorrent(TorrentAction.STOP);
            stop.add(Long.parseLong(torrent.getId()));
            client.execute(stop);
        }

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }

        for (Torrent torrent : torrents) {
            RqTorrent stop = new RqTorrent(TorrentAction.START);
            stop.add(Long.parseLong(torrent.getId()));
            client.execute(stop);
        }

    }

    @Override
    public void close() {
        client.shutdown();
    }
}
