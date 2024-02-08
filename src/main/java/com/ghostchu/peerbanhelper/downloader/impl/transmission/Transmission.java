package com.ghostchu.peerbanhelper.downloader.impl.transmission;

import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import cordelia.client.TrClient;
import cordelia.client.TypedResponse;
import cordelia.rpc.*;
import cordelia.rpc.types.Fields;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class Transmission implements Downloader {

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
        log.warn("[受限] 由于 Transmission 的 RPC-API 限制，PeerId 黑名单功能和 ProgressCheatBlocker 功能的过量下载模块不可用。");
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
        RqTorrentGet torrent = new RqTorrentGet(Fields.ID, Fields.HASH_STRING, Fields.NAME, Fields.PEERS_CONNECTED, Fields.STATUS, Fields.TOTAL_SIZE, Fields.PEERS, Fields.RATE_DOWNLOAD, Fields.RATE_UPLOAD);
        TypedResponse<RsTorrentGet> rsp = client.execute(torrent);
        return rsp.getArgs().getTorrents().stream().map(TRTorrent::new).collect(Collectors.toList());
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
            log.warn("设置 Transmission 的 BanList 地址时，返回非成功响应：{}。", sessionSetResp.getResult());
        }
        RqBlockList updateBlockList = new RqBlockList();
        TypedResponse<RsBlockList> updateBlockListResp = client.execute(updateBlockList);
        if (!updateBlockListResp.isSuccess()) {
            log.warn("请求 Transmission 更新 BanList 时，返回非成功响应：{}。", sessionSetResp.getResult());
        }
    }

    @Override
    public void close() {
        client.shutdown();
    }
}
