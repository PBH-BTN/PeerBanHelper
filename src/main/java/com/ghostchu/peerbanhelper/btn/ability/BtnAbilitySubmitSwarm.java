package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ping.BtnSwarm;
import com.ghostchu.peerbanhelper.btn.ping.BtnSwarmPeerPing;
import com.ghostchu.peerbanhelper.database.dao.impl.MetadataDao;
import com.ghostchu.peerbanhelper.database.dao.impl.tmp.TrackedSwarmDao;
import com.ghostchu.peerbanhelper.database.table.tmp.TrackedSwarmEntity;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.gson.JsonObject;
import com.j256.ormlite.dao.CloseableWrappedIterable;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpResponse;
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

    public BtnAbilitySubmitSwarm(BtnNetwork btnNetwork, JsonObject ability, MetadataDao metadataDao, TrackedSwarmDao swarmDao) {
        this.btnNetwork = btnNetwork;
        this.metadataDao = metadataDao;
        this.swarmDao = swarmDao;
        this.interval = ability.get("interval").getAsLong();
        this.endpoint = ability.get("endpoint").getAsString();
        this.randomInitialDelay = ability.get("random_initial_delay").getAsLong();
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
        btnNetwork.getExecuteService().scheduleWithFixedDelay(this::submit, interval + ThreadLocalRandom.current().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
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
            int size = 0;
            int requests = 0;
            List<TrackedSwarmEntity> trackedSwarmEntities = new ArrayList<>(1000);
            try (var it = createSubmitIterator(getMemCursor())) {
                for (TrackedSwarmEntity entity : it) {
                    trackedSwarmEntities.add(entity);
                    if (trackedSwarmEntities.size() >= 1000) {
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
        MutableRequest request = MutableRequest.POST(endpoint, HTTPUtil.gzipBody(JsonUtil.getGson().toJson(ping).getBytes(StandardCharsets.UTF_8)))
                .header("Content-Encoding", "gzip");
        HttpResponse<String> resp = HTTPUtil.nonRetryableSend(btnNetwork.getHttpClient(), request, HttpResponse.BodyHandlers.ofString()).join();
        if (resp.statusCode() < 200 && resp.statusCode() >= 400) {
            log.error(tlUI(Lang.BTN_REQUEST_FAILS, resp.statusCode() + " - " + resp.body()));
            setLastStatus(false, new TranslationComponent(Lang.BTN_HTTP_ERROR, resp.statusCode(), resp.body()));
            throw new IllegalStateException(tlUI(new TranslationComponent(Lang.BTN_HTTP_ERROR, resp.statusCode(), resp.body())));
        } else {
            return swarmEntities.getLast().getLastTimeSeen();
        }
    }
}
