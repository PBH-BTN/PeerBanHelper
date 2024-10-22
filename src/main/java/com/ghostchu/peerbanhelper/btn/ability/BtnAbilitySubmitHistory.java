package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ping.BtnPeerHistory;
import com.ghostchu.peerbanhelper.btn.ping.BtnPeerHistoryPing;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.paging.Pageable;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.gson.JsonObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class BtnAbilitySubmitHistory extends AbstractBtnAbility {
    private final BtnNetwork btnNetwork;
    private final long interval;
    private final String endpoint;
    private final long randomInitialDelay;
    private long lastSubmitAt = System.currentTimeMillis();

    public BtnAbilitySubmitHistory(BtnNetwork btnNetwork, JsonObject ability) {
        this.btnNetwork = btnNetwork;
        this.interval = ability.get("interval").getAsLong();
        this.endpoint = ability.get("endpoint").getAsString();
        this.randomInitialDelay = ability.get("random_initial_delay").getAsLong();
    }


    @Override
    public void load() {
        setLastStatus(true, tlUI(Lang.BTN_NO_CONTENT_REPORTED_YET));
        btnNetwork.getExecuteService().scheduleWithFixedDelay(this::submit, interval + ThreadLocalRandom.current().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    private void submit() {
        try {
            log.info(tlUI(Lang.BTN_SUBMITTING_PEERS));
            List<BtnPeerHistory> btnPeers = generatePing();
            if (btnPeers.isEmpty()) {
                setLastStatus(true, tlUI(Lang.BTN_LAST_REPORT_EMPTY));
                return;
            }
            BtnPeerHistoryPing ping = new BtnPeerHistoryPing(
                    System.currentTimeMillis(),
                    btnPeers
            );
            MutableRequest request = MutableRequest.POST(endpoint
                    , HTTPUtil.gzipBody(JsonUtil.getGson().toJson(ping).getBytes(StandardCharsets.UTF_8))
            ).header("Content-Encoding", "gzip");
            HTTPUtil.nonRetryableSend(btnNetwork.getHttpClient(), request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(r -> {
                        if (r.statusCode() != 200) {
                            log.error(tlUI(Lang.BTN_REQUEST_FAILS, r.statusCode() + " - " + r.body()));
                            setLastStatus(false, tlUI(Lang.BTN_HTTP_ERROR, r.statusCode(), r.body()));
                        } else {
                            log.info(tlUI(Lang.BTN_SUBMITTED_PEERS, btnPeers.size()));
                            setLastStatus(true, tlUI(Lang.BTN_REPORTED_DATA, btnPeers.size()));
                            lastSubmitAt = System.currentTimeMillis();
                        }
                    })
                    .exceptionally(e -> {
                        log.warn(tlUI(Lang.BTN_REQUEST_FAILS), e);
                        setLastStatus(false, e.getClass().getName() + ": " + e.getMessage());
                        return null;
                    });
        } catch (Throwable e) {
            log.error("Unable to submit peer histories", e);
            setLastStatus(false, "Unknown Error: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }


    @SneakyThrows
    private List<BtnPeerHistory> generatePing() {
        Pageable pageable = new Pageable(0, 10000); // 再多的话，担心爆内存
        return btnNetwork.getPeerRecordDao().getPendingSubmitPeerRecords(pageable,
                        new Timestamp(lastSubmitAt)).getResults().stream()
                .map(BtnPeerHistory::from).collect(Collectors.toList());
    }

    @Override
    public void unload() {

    }


}
