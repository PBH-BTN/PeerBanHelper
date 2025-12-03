package com.ghostchu.peerbanhelper.btn.ability.impl;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ability.AbstractBtnAbility;
import com.ghostchu.peerbanhelper.btn.ping.BtnSwarm;
import com.ghostchu.peerbanhelper.btn.ping.BtnSwarmPeerPing;
import com.ghostchu.peerbanhelper.database.dao.impl.MetadataDao;
import com.ghostchu.peerbanhelper.database.dao.impl.tmp.TrackedSwarmDao;
import com.ghostchu.peerbanhelper.database.table.tmp.TrackedSwarmEntity;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.gson.JsonObject;
import com.j256.ormlite.dao.CloseableWrappedIterable;
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
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class BtnAbilitySubmitSwarm extends AbstractBtnAbility {
    private final BtnNetwork btnNetwork;
    private final long interval;
    private final String endpoint;
    private final long randomInitialDelay;
    private final MetadataDao metadataDao;
    private final TrackedSwarmDao swarmDao;
    private final boolean powCaptcha;

    public BtnAbilitySubmitSwarm(BtnNetwork btnNetwork, JsonObject ability, MetadataDao metadataDao, TrackedSwarmDao swarmDao) {
        this.btnNetwork = btnNetwork;
        this.metadataDao = metadataDao;
        this.swarmDao = swarmDao;
        this.interval = ability.get("interval").getAsLong();
        this.endpoint = ability.get("endpoint").getAsString();
        this.randomInitialDelay = ability.get("random_initial_delay").getAsLong();
        this.powCaptcha = ability.has("pow_captcha") && ability.get("pow_captcha").getAsBoolean();
    }

    @Override
    public String getName() {
        return "BtnAbilitySubmitSwarm";
    }

    @Override
    public TranslationComponent getDisplayName() {
        return new TranslationComponent(Lang.BTN_ABILITY_SUBMIT_SWARM);
    }

    @Override
    public TranslationComponent getDescription() {
        return new TranslationComponent(Lang.BTN_ABILITY_SUBMIT_SWARM_DESCRIPTION);
    }

    @Override
    public void load() {
        Main.getEventBus().register(this);
        setLastStatus(true, new TranslationComponent(Lang.BTN_NO_CONTENT_REPORTED_YET));
        btnNetwork.getScheduler().scheduleWithFixedDelay(this::submit, interval + ThreadLocalRandom.current().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    @Override
    public void unload() {
        Main.getEventBus().unregister(this);
    }

    private int setMemCursor(long position) {
        return metadataDao.set("BtnAbilitySubmitSwarm.memCursor", String.valueOf(position));
    }

    private long getMemCursor() {
        return Long.parseLong(metadataDao.getOrDefault("BtnAbilitySubmitSwarm.memCursor", "0"));
    }

    private void submit() {
        try {
            log.info(tlUI(Lang.BTN_SUBMITTING_SWARM));
            swarmDao.flushAll();
            int size = 0;
            int requests = 0;
            List<TrackedSwarmEntity> trackedSwarmEntities = new ArrayList<>(500);
            try (var it = createSubmitIterator(getMemCursor())) {
                for (TrackedSwarmEntity entity : it) {
                    trackedSwarmEntities.add(entity);
                    if (trackedSwarmEntities.size() >= 500) {
                        setMemCursor(createSubmitRequest(trackedSwarmEntities).getTime());
                        requests++;
                        size += trackedSwarmEntities.size();
                        trackedSwarmEntities.clear();
                    }
                }
            }
            if (!trackedSwarmEntities.isEmpty()) {
                setMemCursor(createSubmitRequest(trackedSwarmEntities).getTime());
                requests++;
                size += trackedSwarmEntities.size();
            }
            log.info(tlUI(Lang.BTN_SUBMITTED_SWARM, size, requests));
            setLastStatus(true, new TranslationComponent(Lang.BTN_REPORTED_DATA, size));
        } catch (IllegalStateException ignored) {
            // 子请求已处理报错信息
        } catch (Throwable e) {
            log.error(tlUI(Lang.BTN_UNKNOWN_ERROR), e);
            setLastStatus(false, new TranslationComponent(Lang.BTN_UNKNOWN_ERROR, e.getClass().getName() + ": " + e.getMessage()));
        }
    }

    private CloseableWrappedIterable<TrackedSwarmEntity> createSubmitIterator(long memCursor) throws SQLException {
        return swarmDao.getWrappedIterable(swarmDao
                .queryBuilder()
                .where().gt("lastTimeSeen", memCursor)
                .queryBuilder()
                .orderBy("lastTimeSeen", true)
                .prepare());
    }

    private Timestamp createSubmitRequest(List<TrackedSwarmEntity> swarmEntities) throws RuntimeException {
        BtnSwarmPeerPing ping = new BtnSwarmPeerPing(swarmEntities.stream().map(BtnSwarm::from).toList());
        byte[] jsonBytes = JsonUtil.getGson().toJson(ping).getBytes(StandardCharsets.UTF_8);
        RequestBody body = createGzipRequestBody(jsonBytes);
        Request.Builder request = new Request.Builder()
                .url(endpoint)
                .post(body)
                .header("Content-Encoding", "gzip");
        if (powCaptcha) btnNetwork.gatherAndSolveCaptchaBlocking(request, "submit_swarm");
        try (Response resp = btnNetwork.getHttpClient().newCall(request.build()).execute()) {
            if (resp.code() < 200 || resp.code() >= 400) {
                String responseBody = resp.body().string();
                log.error(tlUI(Lang.BTN_REQUEST_FAILS, resp.code() + " - " + responseBody));
                setLastStatus(false, new TranslationComponent(Lang.BTN_HTTP_ERROR, resp.code(), responseBody));
                throw new IllegalStateException(tlUI(new TranslationComponent(Lang.BTN_HTTP_ERROR, resp.code(), responseBody)));
            } else {
                return swarmEntities.getLast().getLastTimeSeen();
            }
        } catch (Exception e) {
            log.error(tlUI(Lang.BTN_REQUEST_FAILS, e.getMessage()), e);
            setLastStatus(false, new TranslationComponent(Lang.BTN_HTTP_ERROR, -1, e.getMessage()));
            throw new IllegalStateException(tlUI(new TranslationComponent(Lang.BTN_HTTP_ERROR, -1, e.getMessage())), e);
        }
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
