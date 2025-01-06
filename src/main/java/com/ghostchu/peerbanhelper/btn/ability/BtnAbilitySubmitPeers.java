package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ping.BtnPeer;
import com.ghostchu.peerbanhelper.btn.ping.BtnPeerPing;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class BtnAbilitySubmitPeers extends AbstractBtnAbility {
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
    public String getName() {
        return "BtnAbilitySubmitPeers";
    }

    @Override
    public TranslationComponent getDisplayName() {
        return new TranslationComponent(Lang.BTN_ABILITY_SUBMIT_SNAPSHOT);
    }

    @Override
    public TranslationComponent getDescription() {
        return new TranslationComponent(Lang.BTN_ABILITY_SUBMIT_SNAPSHOT_DESCRIPTION);
    }

    @Override
    public void load() {
        setLastStatus(true, new TranslationComponent(Lang.BTN_NO_CONTENT_REPORTED_YET));
        btnNetwork.getExecuteService().scheduleWithFixedDelay(this::submit, interval + ThreadLocalRandom.current().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    private void submit() {
        try {
            log.info(tlUI(Lang.BTN_SUBMITTING_PEERS));
            List<BtnPeer> btnPeers = generatePing();
            if (btnPeers.isEmpty()) {
                setLastStatus(true, new TranslationComponent(Lang.BTN_LAST_REPORT_EMPTY));
                return;
            }
            BtnPeerPing ping = new BtnPeerPing(System.currentTimeMillis(), btnPeers);
            MutableRequest request = MutableRequest.POST(endpoint, HTTPUtil.gzipBody(JsonUtil.getGson().toJson(ping).getBytes(StandardCharsets.UTF_8))).header("Content-Encoding", "gzip");
            HTTPUtil.nonRetryableSend(btnNetwork.getHttpClient(), request, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
                if (r.statusCode() != 200) {
                    log.error(tlUI(Lang.BTN_REQUEST_FAILS, r.statusCode() + " - " + r.body()));
                    setLastStatus(false, new TranslationComponent(Lang.BTN_HTTP_ERROR, r.statusCode(), r.body()));
                } else {
                    log.info(tlUI(Lang.BTN_SUBMITTED_PEERS, btnPeers.size()));
                    setLastStatus(true, new TranslationComponent(Lang.BTN_REPORTED_DATA, btnPeers.size()));
                }
            }).exceptionally(e -> {
                log.warn(tlUI(Lang.BTN_REQUEST_FAILS), e);
                setLastStatus(false, new TranslationComponent(e.getClass().getName() + ": " + e.getMessage()));
                return null;
            });
        } catch (Throwable e) {
            log.error(tlUI(Lang.BTN_SUBMIT_PEERS_FAILED), e);
            setLastStatus(false, new TranslationComponent(Lang.BTN_UNKNOWN_ERROR, e.getClass().getName() + ": " + e.getMessage()));
        }
    }


    private List<BtnPeer> generatePing() {
        List<BtnPeer> btnPeers = new ArrayList<>();
        btnNetwork.getServer().getLivePeersSnapshot().forEach((pa, pm) -> pm.forEach(meta -> btnPeers.add(BtnPeer.from(meta.getTorrent(), meta.getPeer()))));
        return btnPeers;
    }

    @Override
    public void unload() {

    }


}
