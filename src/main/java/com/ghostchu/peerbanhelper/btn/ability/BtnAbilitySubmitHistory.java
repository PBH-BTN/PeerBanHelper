package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.Main;
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

import java.io.File;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class BtnAbilitySubmitHistory extends AbstractBtnAbility {
    private final BtnNetwork btnNetwork;
    private final long interval;
    private final String endpoint;
    private final long randomInitialDelay;
    private final File file;
    private final Lock lock = new ReentrantLock();

    public BtnAbilitySubmitHistory(BtnNetwork btnNetwork, JsonObject ability) {
        this.btnNetwork = btnNetwork;
        this.interval = ability.get("interval").getAsLong();
        this.endpoint = ability.get("endpoint").getAsString();
        this.randomInitialDelay = ability.get("random_initial_delay").getAsLong();

        this.file = new File(Main.getDataDirectory(), "btn_submit_history_timestamp.dat");
        if (!this.file.exists()) {
            try {
                this.file.createNewFile();
                writeLastSubmitAtTimestamp(System.currentTimeMillis());
            } catch (Exception e) {
                log.error("Unable to create file for record btn submit_history_timestamp, default to current timestamp", e);
            }
        }
    }

    private void writeLastSubmitAtTimestamp(long timestamp) {
        try {
            Files.writeString(file.toPath(), String.valueOf(timestamp));
        } catch (IOException e) {
            log.error("Unable to write timestamp to file", e);
        }
    }

    private long getLastSubmitAtTimestamp() {
        try {
            long time = Long.parseLong(Files.readString(file.toPath()));
            // check if timestamp more than past 30 days
            if (time < System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L) {
                // discard any outdated data and use current time
                return System.currentTimeMillis();
            }
            return time;
        } catch (Exception e) {
            log.error("Unable to read timestamp from file", e);
            return System.currentTimeMillis();
        }
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
        if (!lock.tryLock()) {
            return; // this is possible since we can send multiple requests
        }
        try {
            log.info(tlUI(Lang.BTN_SUBMITTING_HISTORIES));
            AtomicLong lastSubmitAt = new AtomicLong(getLastSubmitAtTimestamp());
            List<BtnPeerHistory> btnPeers = generatePing(lastSubmitAt.get());
            while (!btnPeers.isEmpty()) {
                BtnPeerHistoryPing ping = new BtnPeerHistoryPing(
                        System.currentTimeMillis(),
                        btnPeers
                );
                MutableRequest request = MutableRequest.POST(endpoint
                        , HTTPUtil.gzipBody(JsonUtil.getGson().toJson(ping).getBytes(StandardCharsets.UTF_8))
                ).header("Content-Encoding", "gzip");
                HTTPUtil.nonRetryableSend(btnNetwork.getHttpClient(), request, HttpResponse.BodyHandlers.ofString())
                        .orTimeout(30, TimeUnit.SECONDS)
                        .thenAccept(r -> {
                            if (r.statusCode() != 200) {
                                log.error(tlUI(Lang.BTN_REQUEST_FAILS, r.statusCode() + " - " + r.body()));
                                setLastStatus(false, new TranslationComponent(Lang.BTN_HTTP_ERROR, r.statusCode(), r.body()));
                            } else {
                                log.info(tlUI(Lang.BTN_SUBMITTED_HISTORIES, ping.getPeers().size()));
                                setLastStatus(true, new TranslationComponent(Lang.BTN_REPORTED_DATA, ping.getPeers().size()));
                            }
                        })
                        .exceptionally(e -> {
                            log.warn(tlUI(Lang.BTN_REQUEST_FAILS), e);
                            setLastStatus(false, new TranslationComponent(e.getClass().getName() + ": " + e.getMessage()));
                            return null;
                        })
                        .join();
                var lastRecordAt = btnPeers.getLast().getLastTimeSeen().getTime();
                if (lastRecordAt >= lastSubmitAt.get()) {
                    if (lastRecordAt == lastSubmitAt.get()) {
                        lastRecordAt++; // avoid inf loop
                    }
                    lastSubmitAt.set(lastRecordAt);
                    writeLastSubmitAtTimestamp(lastRecordAt);
                } else {
                    lastSubmitAt.set(System.currentTimeMillis());
                    writeLastSubmitAtTimestamp(System.currentTimeMillis());
                }
                btnPeers = generatePing(lastSubmitAt.get());
            }

        } catch (Throwable e) {
            log.error("Unable to submit peer histories", e);
            setLastStatus(false, new TranslationComponent("Unknown Error: " + e.getClass().getName() + ": " + e.getMessage()));
        } finally {
            lock.unlock();
        }
    }


    @SneakyThrows
    private List<BtnPeerHistory> generatePing(long lastSubmitAt) {
        Pageable pageable = new Pageable(0, 5000);
        return btnNetwork.getPeerRecordDao().getPendingSubmitPeerRecords(pageable,
                        new Timestamp(lastSubmitAt)).getResults().stream()
                .map(BtnPeerHistory::from).collect(Collectors.toList());
    }

    @Override
    public void unload() {

    }


}
