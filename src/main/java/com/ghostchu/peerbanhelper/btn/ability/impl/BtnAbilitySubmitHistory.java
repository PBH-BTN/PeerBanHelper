package com.ghostchu.peerbanhelper.btn.ability.impl;

import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ability.AbstractBtnAbility;
import com.ghostchu.peerbanhelper.btn.ping.legacy.LegacyBtnPeerHistory;
import com.ghostchu.peerbanhelper.btn.ping.legacy.LegacyBtnPeerHistoryPing;
import com.ghostchu.peerbanhelper.databasent.service.MetadataService;
import com.ghostchu.peerbanhelper.databasent.service.PeerRecordService;
import com.ghostchu.peerbanhelper.databasent.service.TorrentService;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.TorrentEntityDTO;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.backgroundtask.FunctionalBackgroundTask;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
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
    private final Lock lock = new ReentrantLock();
    private final boolean powCaptcha;
    private final MetadataService metadataDao;
    private final TorrentService torrentService;
    private final PeerRecordService peerRecordService;

    public BtnAbilitySubmitHistory(BtnNetwork btnNetwork, MetadataService metadataDao, JsonObject ability, TorrentService torrentService, PeerRecordService peerRecordService) {
        this.btnNetwork = btnNetwork;
        this.interval = ability.get("interval").getAsLong();
        this.endpoint = ability.get("endpoint").getAsString();
        this.randomInitialDelay = ability.get("random_initial_delay").getAsLong();
        this.powCaptcha = ability.has("pow_captcha") && ability.get("pow_captcha").getAsBoolean();
        this.metadataDao = metadataDao;
        this.torrentService = torrentService;
        this.peerRecordService = peerRecordService;

        if (metadataDao.get("btn.submithistory.timestamp") == null) {
            writeLastSubmitAtTimestamp(System.currentTimeMillis());
        }
    }

    private void writeLastSubmitAtTimestamp(long timestamp) {
        metadataDao.set("btn.submithistory.timestamp", String.valueOf(timestamp));
    }

    private long getLastSubmitAtTimestamp() {
        try {
            long time = Long.parseLong(metadataDao.getOrDefault("btn.submithistory.timestamp", "0"));
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
        btnNetwork.getScheduler().scheduleWithFixedDelay(this::submit, ThreadLocalRandom.current().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    private void submit() {
        if (!lock.tryLock()) {
            return; // this is possible since we can send multiple requests
        }
        try {
            btnNetwork.getBackgroundTaskManager().addTaskAsync(new FunctionalBackgroundTask(new TranslationComponent(Lang.BTN_ABILITY_SUBMIT_HISTORY_SYNC_SERVER), (task, callback) -> {
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
                    Request.Builder request = new Request.Builder()
                            .url(endpoint)
                            .post(body)
                            .header("Content-Encoding", "gzip");
                    if (powCaptcha) btnNetwork.gatherAndSolveCaptchaBlocking(request, "submit_history");
                    try (Response response = btnNetwork.getHttpClient().newCall(request.build()).execute()) {
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
            })).join();
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
        return peerRecordService.getPendingSubmitPeerRecords(pageable,
                        OffsetDateTime.from(Instant.ofEpochMilli(lastSubmitAt))).getRecords().stream()
                .map(e -> {
                    TorrentEntityDTO dto = TorrentEntityDTO.from(torrentService.getById(e.getTorrentId()));
                    return LegacyBtnPeerHistory.from(e, dto);
                }).collect(Collectors.toList());
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