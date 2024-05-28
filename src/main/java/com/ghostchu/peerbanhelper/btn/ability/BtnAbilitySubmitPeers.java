package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ping.BtnPeer;
import com.ghostchu.peerbanhelper.btn.ping.BtnPeerPing;
import com.ghostchu.peerbanhelper.text.Lang;
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
        HTTPUtil.nonRetryableSend(btnNetwork.getHttpClient(), request, HttpResponse.BodyHandlers.ofString())
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
        btnNetwork.getServer().getLivePeersSnapshot().forEach((pa, pm) -> {
            btnPeers.add(BtnPeer.from(pm.getTorrent(), pm.getPeer()));
        });
        return btnPeers;
    }

    @Override
    public void unload() {

    }


}
