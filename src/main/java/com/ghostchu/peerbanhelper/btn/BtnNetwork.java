package com.ghostchu.peerbanhelper.btn;

import com.ghostchu.peerbanhelper.btn.ping.*;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.util.URLUtil;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class BtnNetwork {
    private final BtnManager btnManager;
    private final String appId;
    private final String appSecret;
    private final boolean submit;
    @Setter
    @Getter
    private BtnRule rule;

    public BtnNetwork(BtnManager btnManager, String appId, String appSecret, boolean submit) {
        this.btnManager = btnManager;
        this.appId = appId;
        this.appSecret = appSecret;
        this.submit = submit;

    }

    public void updateRule() {
        if (!btnManager.getBtnConfig().getAbility().contains("rule")) {
            return;
        }
        try {
            String version;
            if (rule == null || rule.getVersion() == null) {
                version = "0";
            } else {
                version = rule.getVersion();
            }
            HttpResponse<String> resp = btnManager.getHttpClient()
                    .send(MutableRequest.GET(URLUtil.appendUrl(btnManager.getBtnConfig().getEndpoint().getRule(), Map.of("rev", version))), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 204) {
                return;
            }
            if (resp.statusCode() != 200) {
                log.warn(Lang.BTN_REQUEST_FAILS, resp.statusCode() + " - " + resp.body());
            } else {
                this.rule = JsonUtil.getGson().fromJson(resp.body(), BtnRule.class);
                log.info(Lang.BTN_UPDATE_RULES_SUCCESSES, this.rule.getVersion());
            }
        } catch (IOException | InterruptedException e) {
            log.warn(Lang.BTN_REQUEST_FAILS, e);
        }
    }

    public void submit() {
        if (!submit) {
            return;
        }
        if (!btnManager.getBtnConfig().getAbility().contains("submit")) {
            return;
        }
        List<ClientPing> pings = generatePings();
        List<List<ClientPing>> batch = Lists.partition(pings, btnManager.getBtnConfig().getThreshold().getPerBatchSize());
        log.info(Lang.BTN_PREPARE_TO_SUBMIT, pings.stream().mapToLong(p -> p.getPeers().size()).sum(), batch.size());
        for (int i = 0; i < batch.size(); i++) {
            List<ClientPing> clientPing = batch.get(i);
            submitPings(clientPing, i, batch.size());
        }
    }


    private void submitPings(List<ClientPing> clientPings, int batchIndex, int batchSize) {
        clientPings.forEach(ping -> {
            ping.setBatchIndex(batchIndex);
            ping.setBatchSize(batchSize);
            try {
                MutableRequest request = MutableRequest.POST(btnManager.getBtnConfig().getEndpoint().getPing()
                        , HTTPUtil.gzipBody(JsonUtil.getGson().toJson(ping).getBytes(StandardCharsets.UTF_8))
                ).header("Content-Encoding", "gzip");
                btnManager.getHttpClient().send(request
                        , HttpResponse.BodyHandlers.discarding());
                Thread.sleep(btnManager.getBtnConfig().getThreshold().getBatchPeriod());
            } catch (IOException | InterruptedException e) {
                log.warn(Lang.BTN_REQUEST_FAILS, e);
            }
        });
    }

    private List<ClientPing> generatePings() {
        List<ClientPing> clientPings = new ArrayList<>();
        for (Downloader downloader : btnManager.getServer().getDownloaders()) {
            List<PeerConnection> peerConnections = new ArrayList<>();
            try {
                downloader.login();
                for (Torrent torrent : downloader.getTorrents()) {
                    try {
                        String salt = Hashing.crc32().hashString(torrent.getHash(), StandardCharsets.UTF_8).toString();
                        String torrentHash = Hashing.sha256().hashString(torrent.getHash() + salt, StandardCharsets.UTF_8).toString();
                        TorrentInfo torrentInfo = new TorrentInfo(torrentHash, torrent.getSize());
                        for (Peer peer : downloader.getPeers(torrent)) {
                            PeerInfo peerInfo = generatePeerInfo(peer);
                            peerConnections.add(new PeerConnection(torrentInfo, peerInfo));
                        }
                    } catch (Exception ignored) {
                    }
                }
                ClientPing ping = new ClientPing();
                ping.setPopulateAt(System.currentTimeMillis());
                ping.setDownloader(downloader.getDownloaderName());
                ping.setPeers(peerConnections);
                clientPings.add(ping);
            } catch (Exception e) {
                log.warn(Lang.BTN_DOWNLOADER_GENERAL_FAILURE, downloader.getName(), e);
            }
        }
        return clientPings;
    }


    @NotNull
    private PeerInfo generatePeerInfo(Peer peer) {
        PeerInfo peerInfo = new PeerInfo();
        peerInfo.setAddress(new PeerAddress(peer.getAddress().getIp(), peer.getAddress().getPort()));
        peerInfo.setClientName(peer.getClientName());
        peerInfo.setPeerId(peer.getPeerId());
        peerInfo.setFlag("N/A");
        peerInfo.setProgress(peer.getProgress());
        peerInfo.setDownloaded(peer.getDownloaded());
        peerInfo.setRtDownloadSpeed(peer.getDownloadSpeed());
        peerInfo.setUploaded(peer.getUploaded());
        peerInfo.setRtUploadSpeed(peer.getUploadedSpeed());
        return peerInfo;
    }
}
