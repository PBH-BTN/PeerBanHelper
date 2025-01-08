package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ping.BtnPeerHistory;
import com.ghostchu.peerbanhelper.btn.ping.BtnPeerHistoryPing;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
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
public final class BtnAbilitySubmitHistory extends AbstractBtnAbility {
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
    public String getName() {
        return "BtnAbilitySubmitHistory";
    }

    @Override
    public TranslationComponent getDisplayName() {
        return new TranslationComponent(Lang.BTN_ABILITY_SUBMIT_HISTORY);
    }

    @Override
    public TranslationComponent getDescription() {
        return new TranslationComponent(Lang.BTN_ABILITY_SUBMIT_HISTORY_DESCRIPTION);
    }

    @Override
    public void load() {
        setLastStatus(true, new TranslationComponent(Lang.BTN_NO_CONTENT_REPORTED_YET));
        btnNetwork.getExecuteService().scheduleWithFixedDelay(this::submit, interval + ThreadLocalRandom.current().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    private void submit() {
        try {
            log.info(tlUI(Lang.BTN_SUBMITTING_HISTORIES));
            List<BtnPeerHistory> btnPeers = generatePing();
            if (btnPeers.isEmpty()) {
                setLastStatus(true, new TranslationComponent(Lang.BTN_LAST_REPORT_EMPTY));
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
                            setLastStatus(false, new TranslationComponent(Lang.BTN_HTTP_ERROR, r.statusCode(), r.body()));
                        } else {
                            log.info(tlUI(Lang.BTN_SUBMITTED_HISTORIES, btnPeers.size()));
                            setLastStatus(true, new TranslationComponent(Lang.BTN_REPORTED_DATA, btnPeers.size()));
                            lastSubmitAt = System.currentTimeMillis();
                        }
                    })
                    .exceptionally(e -> {
                        log.warn(tlUI(Lang.BTN_REQUEST_FAILS), e);
                        setLastStatus(false, new TranslationComponent(e.getClass().getName() + ": " + e.getMessage()));
                        return null;
                    });
        } catch (Throwable e) {
            log.error("Unable to submit peer histories", e);
            setLastStatus(false, new TranslationComponent("Unknown Error: " + e.getClass().getName() + ": " + e.getMessage()));
        }
    }


    /**
     * Generates a list of peer history records pending submission.
     *
     * @return A list of {@link BtnPeerHistory} objects representing peer records that are ready to be submitted.
     *         The list is limited to 10,000 records to prevent memory overflow.
     *
     * @implNote This method retrieves pending peer records from the database that were created after the last submission,
     *           excluding records with a downloader marked as "<UNBAN UPDATE>".
     *
     * @throws RuntimeException if there are issues retrieving or processing peer records
     */
    @SneakyThrows
    private List<BtnPeerHistory> generatePing() {
        Pageable pageable = new Pageable(0, 10000); // 再多的话，担心爆内存
        return btnNetwork.getPeerRecordDao().getPendingSubmitPeerRecords(pageable,
                        new Timestamp(lastSubmitAt)).getResults().stream()
                .filter(r -> !r.getDownloader().equals("<UNBAN UPDATE>"))
                .map(BtnPeerHistory::from).collect(Collectors.toList());
    }

    /**
     * Unloads the ability and performs any necessary cleanup.
     *
     * Currently, this method is a no-op and does not perform any specific actions.
     * It is intended to provide a hook for potential future resource release or cleanup operations
     * when the ability is being disabled or removed.
     */
    @Override
    public void unload() {

    }


}
