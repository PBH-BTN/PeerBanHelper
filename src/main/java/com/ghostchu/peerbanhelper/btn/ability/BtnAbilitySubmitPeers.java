package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ping.BtnPeer;
import com.ghostchu.peerbanhelper.btn.ping.BtnPeerPing;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class BtnAbilitySubmitPeers extends AbstractBtnAbility {
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
        setLastStatus(true, "No content reported to remote yet");
        btnNetwork.getExecuteService().scheduleWithFixedDelay(this::submit, interval + ThreadLocalRandom.current().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    private void submit() {
        try {
            log.info(tlUI(Lang.BTN_SUBMITTING_PEERS));
            List<BtnPeer> btnPeers = generatePing();
            if (btnPeers.isEmpty()) {
                setLastStatus(true, "Last report is empty, skipped.");
                return;
            }
            BtnPeerPing ping = new BtnPeerPing(
                    System.currentTimeMillis(),
                    btnPeers
            );
            Request request = new Request.Builder()
                    .url(endpoint)
                    .post(HTTPUtil.gzipBody(JsonUtil.getGson().toJson(ping).getBytes(StandardCharsets.UTF_8)))
                    .header("Content-Encoding", "gzip")
                    .build();
            HTTPUtil.nonRetryableSend(btnNetwork.getHttpClient(), request)
                    .thenAccept(r -> {
                        if (r.code() != 200) {
                            try {
                                String body = r.body().string();
                                log.error(tlUI(Lang.BTN_REQUEST_FAILS, r.code() + " - " + body));
                                setLastStatus(false, "HTTP Error: " + r.code() + " - " + body);
                            } catch (IOException ignored) {
                                setLastStatus(false, "IO Error");
                            }
                        } else {
                            log.info(tlUI(Lang.BTN_SUBMITTED_PEERS, btnPeers.size()));
                            setLastStatus(true, "Reported " + btnPeers.size() + " entries.");
                        }
                    })
                    .exceptionally(e -> {
                        log.warn(tlUI(Lang.BTN_REQUEST_FAILS), e);
                        setLastStatus(false, e.getClass().getName() + ": " + e.getMessage());
                        return null;
                    });
        } catch (Throwable e) {
            log.error("Unable to submit peers", e);
            setLastStatus(false, "Unknown Error: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }


    private List<BtnPeer> generatePing() {
        List<BtnPeer> btnPeers = new ArrayList<>();
        btnNetwork.getServer().getLivePeersSnapshot().forEach((pa, pm) ->
                pm.forEach(meta -> btnPeers.add(BtnPeer.from(meta.getTorrent(), meta.getPeer()))));
        return btnPeers;
    }

    @Override
    public void unload() {

    }


}
