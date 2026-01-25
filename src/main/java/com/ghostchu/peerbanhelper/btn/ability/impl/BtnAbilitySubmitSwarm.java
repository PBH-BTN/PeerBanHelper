package com.ghostchu.peerbanhelper.btn.ability.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ability.AbstractBtnAbility;
import com.ghostchu.peerbanhelper.btn.ping.BtnSwarm;
import com.ghostchu.peerbanhelper.btn.ping.BtnSwarmPeerPing;
import com.ghostchu.peerbanhelper.databasent.service.MetadataService;
import com.ghostchu.peerbanhelper.databasent.service.TrackedSwarmService;
import com.ghostchu.peerbanhelper.databasent.table.tmp.TrackedSwarmEntity;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.GzipSink;
import okio.Okio;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    private final MetadataService metadataDao;
    private final TrackedSwarmService swarmDao;
    private final boolean powCaptcha;

    public BtnAbilitySubmitSwarm(BtnNetwork btnNetwork, JsonObject ability, MetadataService metadataDao, TrackedSwarmService swarmDao) {
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
        btnNetwork.getScheduler().scheduleWithFixedDelay(this::submit, ThreadLocalRandom.current().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    @Override
    public void unload() {
        Main.getEventBus().unregister(this);
    }

    private boolean setMemCursor(long lastTimeSeen, long id) {
        return metadataDao.set("BtnAbilitySubmitSwarm.cursor", lastTimeSeen + "," + id);
    }

    private Pair<Long, Long> getMemCursor() {
        String[] memCursor = metadataDao.getOrDefault("BtnAbilitySubmitSwarm.cursor", "0,0").split(",");
        return Pair.of(Long.parseLong(memCursor[0]), Long.parseLong(memCursor[1]));
    }

    private void submit() {
        try {
            log.info(tlUI(Lang.BTN_SUBMITTING_SWARM));
            swarmDao.flushAll();

            int size = 0;
            int requests = 0;
            Page<TrackedSwarmEntity> page = new Page<>(1, 1000); // 每页处理 100 条
            do {
                var pair = getMemCursor();
                long lastTimeSeen = pair.getLeft();
                long id = pair.getRight();
                var result = swarmDao.page(page, new QueryWrapper<TrackedSwarmEntity>()
                        .ge("last_time_seen", lastTimeSeen)
                        .gt("id", id)
                        .orderByAsc("last_time_seen", "id")
                );
                if (result.getRecords().isEmpty()) break;
                var resultPair = createSubmitRequest(result.getRecords());
                setMemCursor(resultPair.getLeft(), resultPair.getRight());
                requests++;
                size += result.getRecords().size();
            } while (page.hasNext());
            log.info(tlUI(Lang.BTN_SUBMITTED_SWARM, size, requests));
            setLastStatus(true, new TranslationComponent(Lang.BTN_REPORTED_DATA, size));
        } catch (IllegalStateException ignored) {
            // 子请求已处理报错信息
        } catch (Throwable e) {
            log.error(tlUI(Lang.BTN_UNKNOWN_ERROR), e);
            setLastStatus(false, new TranslationComponent(Lang.BTN_UNKNOWN_ERROR, e.getClass().getName() + ": " + e.getMessage()));
        }
    }


    private Pair<Long, Long> createSubmitRequest(List<TrackedSwarmEntity> swarmEntities) throws RuntimeException {
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
                return Pair.of(swarmEntities.getLast().getLastTimeSeen().toInstant().toEpochMilli(), swarmEntities.getLast().getId());
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
