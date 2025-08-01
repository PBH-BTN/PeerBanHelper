package com.ghostchu.peerbanhelper.btn.ability.impl.legacy;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ability.AbstractBtnAbility;
import com.ghostchu.peerbanhelper.btn.ping.legacy.LegacyBtnPeerHistory;
import com.ghostchu.peerbanhelper.btn.ping.legacy.LegacyBtnPeerHistoryPing;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.google.gson.JsonObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.GzipSink;
import okio.Okio;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
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
public final class LegacyBtnAbilitySubmitHistory extends AbstractBtnAbility {
    private final BtnNetwork btnNetwork;
    private final long interval;
    private final String endpoint;
    private final long randomInitialDelay;
    private final File file;
    private final Lock lock = new ReentrantLock();

    public LegacyBtnAbilitySubmitHistory(BtnNetwork btnNetwork, JsonObject ability) {
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
            writeLastSubmitAtTimestamp(System.currentTimeMillis());
            return System.currentTimeMillis();
        }
    }


    @Override
    public String getName() {
        return "BtnAbilitySubmitHistory (Legacy)";
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
        btnNetwork.getScheduler().scheduleWithFixedDelay(this::submit, interval + ThreadLocalRandom.current().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    private void submit() {
        if (!lock.tryLock()) {
            return; // this is possible since we can send multiple requests
        }
        try {
            log.info(tlUI(Lang.BTN_SUBMITTING_HISTORIES));
            AtomicLong lastSubmitAt = new AtomicLong(getLastSubmitAtTimestamp());
            List<LegacyBtnPeerHistory> btnPeers = generatePing(lastSubmitAt.get());
            while (!btnPeers.isEmpty()) {
                LegacyBtnPeerHistoryPing ping = new LegacyBtnPeerHistoryPing(
                        System.currentTimeMillis(),
                        btnPeers
                );
                byte[] jsonBytes = JsonUtil.getGson().toJson(ping).getBytes(StandardCharsets.UTF_8);
                RequestBody body = createGzipRequestBody(jsonBytes);
                Request request = new Request.Builder()
                        .url(endpoint)
                        .post(body)
                        .header("Content-Encoding", "gzip")
                        .build();
                
                try (Response response = btnNetwork.getHttpClient().newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String responseBody = response.body().string();
                        log.error(tlUI(Lang.BTN_REQUEST_FAILS, response.code() + " - " + responseBody));
                        setLastStatus(false, new TranslationComponent(Lang.BTN_HTTP_ERROR, response.code(), responseBody));
                        return;
                    } else {
                        log.info(tlUI(Lang.BTN_SUBMITTED_HISTORIES, ping.getPeers().size()));
                        setLastStatus(true, new TranslationComponent(Lang.BTN_REPORTED_DATA, ping.getPeers().size()));
                    }
                } catch (Exception e) {
                    log.warn(tlUI(Lang.BTN_REQUEST_FAILS), e);
                    setLastStatus(false, new TranslationComponent(e.getClass().getName() + ": " + e.getMessage()));
                    return;
                }
                
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
    private List<LegacyBtnPeerHistory> generatePing(long lastSubmitAt) {
        Pageable pageable = new Pageable(0, 5000);
        return btnNetwork.getPeerRecordDao().getPendingSubmitPeerRecords(pageable,
                        new Timestamp(lastSubmitAt)).getResults().stream()
                .map(LegacyBtnPeerHistory::from).collect(Collectors.toList());
    }

    @Override
    public void unload() {

    }

    private RequestBody createGzipRequestBody(byte[] data) {
        return new RequestBody() {
            @Override
            public okhttp3.MediaType contentType() {
                return okhttp3.MediaType.get("application/json");
            }

            @Override
            public void writeTo(@NotNull BufferedSink sink) throws IOException {
                BufferedSink gzipSink = Okio.buffer(new GzipSink(sink));
                gzipSink.write(data);
                gzipSink.close();
            }
        };
    }
}