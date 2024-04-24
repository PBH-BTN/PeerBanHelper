package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ping.BtnPeer;
import com.ghostchu.peerbanhelper.btn.ping.BtnPeerPing;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BtnAbilitySubmitPeers implements BtnAbility {
    private final BtnNetwork btnNetwork;
    private final long interval;
    private final String endpoint;
    private final long randomInitialDelay;

    public BtnAbilitySubmitPeers(BtnNetwork btnNetwork, JsonObject ability) {
        this.btnNetwork = btnNetwork;
        this.interval = ability.get("interval").getAsLong();
        this.endpoint = ability.get("endpoint").getAsString();
        this.randomInitialDelay = ability.get("random_initial_delay").getAsLong();
    }


    @Override
    public void load() {
        btnNetwork.getExecuteService().scheduleAtFixedRate(this::submit, interval + new Random().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    private void submit() {
        log.info(Lang.BTN_SUBMITTING_PEERS);
        List<BtnPeer> btnPeers = generatePing();
        BtnPeerPing ping = new BtnPeerPing(
                System.currentTimeMillis(),
                btnPeers
        );
        MutableRequest request = MutableRequest.POST(endpoint
                , HTTPUtil.gzipBody(JsonUtil.getGson().toJson(ping).getBytes(StandardCharsets.UTF_8))
        ).header("Content-Encoding", "gzip");
        HTTPUtil.retryableSend(btnNetwork.getHttpClient(), request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(r -> {
                    if (r.statusCode() != 200) {
                        log.warn(Lang.BTN_REQUEST_FAILS, r.statusCode() + " - " + r.body());
                    } else {
                        log.info(Lang.BTN_SUBMITTED_PEERS, btnPeers.size());
                    }
                })
                .exceptionally(e -> {
                    log.warn(Lang.BTN_REQUEST_FAILS, e);
                    return null;
                });
    }


    private List<BtnPeer> generatePing() {
        List<BtnPeer> btnPeers = new ArrayList<>();
        for (Downloader downloader : btnNetwork.getServer().getDownloaders()) {
            try {
                downloader.login();
                for (Torrent torrent : downloader.getTorrents()) {
                    try {
                        for (Peer peer : downloader.getPeers(torrent)) {
                            btnPeers.add(BtnPeer.from(torrent, peer));
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception e) {
                log.warn(Lang.BTN_DOWNLOADER_GENERAL_FAILURE, downloader.getName(), e);
            }
        }
        return btnPeers;
    }

    @Override
    public void unload() {

    }


}
