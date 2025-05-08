package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ping.BtnBan;
import com.ghostchu.peerbanhelper.btn.ping.BtnBanPing;
import com.ghostchu.peerbanhelper.database.dao.impl.HistoryDao;
import com.ghostchu.peerbanhelper.database.dao.impl.MetadataDao;
import com.ghostchu.peerbanhelper.database.table.HistoryEntity;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class BtnAbilitySubmitBans extends AbstractBtnAbility {
    private final BtnNetwork btnNetwork;
    private final long interval;
    private final String endpoint;
    private final long randomInitialDelay;
    private final MetadataDao metadataDao;
    private final HistoryDao historyDao;

    public BtnAbilitySubmitBans(BtnNetwork btnNetwork, JsonObject ability, MetadataDao metadataDao, HistoryDao historyDao) {
        this.btnNetwork = btnNetwork;
        this.metadataDao = metadataDao;
        this.historyDao = historyDao;
        this.interval = ability.get("interval").getAsLong();
        this.endpoint = ability.get("endpoint").getAsString();
        this.randomInitialDelay = ability.get("random_initial_delay").getAsLong();
    }

    @Override
    public String getName() {
        return "BtnAbilitySubmitBans";
    }

    @Override
    public TranslationComponent getDisplayName() {
        return new TranslationComponent(Lang.BTN_ABILITY_SUBMIT_BANS);
    }

    @Override
    public TranslationComponent getDescription() {
        return new TranslationComponent(Lang.BTN_ABILITY_SUBMIT_BANS_DESCRIPTION);
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
        return metadataDao.set("BtnAbilitySubmitBans.memCursor", String.valueOf(position));
    }

    private long getMemCursor() {
        return Long.parseLong(metadataDao.getOrDefault("BtnAbilitySubmitBans.memCursor", "0"));
    }

    private void submit() {
        try {
            log.info(tlUI(Lang.BTN_SUBMITTING_BANS));
            int size = 0;
            int requests = 0;
            List<HistoryEntity> historyEntities = new ArrayList<>(1000);
            try (var it = createSubmitIterator(getMemCursor())) {
                for (HistoryEntity entity : it) {
                    historyEntities.add(entity);
                    if (historyEntities.size() >= 1000) {
                        setMemCursor(createSubmitRequest(historyEntities));
                        size += historyEntities.size();
                        requests++;
                        historyEntities.clear();
                    }
                }
            }
            if (!historyEntities.isEmpty()) {
                setMemCursor(createSubmitRequest(historyEntities));
                requests++;
                size += historyEntities.size();
            }
            log.info(tlUI(Lang.BTN_SUBMITTED_BANS, size, requests));
            setLastStatus(true, new TranslationComponent(Lang.BTN_REPORTED_DATA, size));
        } catch (IllegalStateException ignored) {
            // 子请求已处理报错信息
        } catch (Throwable e) {
            log.error(tlUI(Lang.BTN_UNKNOWN_ERROR), e);
            setLastStatus(false, new TranslationComponent(Lang.BTN_UNKNOWN_ERROR, e.getClass().getName() + ": " + e.getMessage()));
        }
    }

    private CloseableWrappedIterable<HistoryEntity> createSubmitIterator(long memCursor) throws SQLException {
        return historyDao.getWrappedIterable(historyDao
                .queryBuilder()
                .where().gt("id", memCursor)
                .queryBuilder()
                .orderBy("id", true)
                .prepare());
    }

    private long createSubmitRequest(List<HistoryEntity> historyEntities) throws RuntimeException {
        BtnBanPing ping = new BtnBanPing(historyEntities.stream().map(BtnBan::from).toList());
        MutableRequest request = MutableRequest.POST(endpoint, HTTPUtil.gzipBody(JsonUtil.getGson().toJson(ping).getBytes(StandardCharsets.UTF_8)))
                .header("Content-Encoding", "gzip");
        HttpResponse<String> resp = HTTPUtil.nonRetryableSend(btnNetwork.getHttpClient(), request, HttpResponse.BodyHandlers.ofString()).join();
        if (resp.statusCode() < 200 && resp.statusCode() >= 400) {
            log.error(tlUI(Lang.BTN_REQUEST_FAILS, resp.statusCode() + " - " + resp.body()));
            setLastStatus(false, new TranslationComponent(Lang.BTN_HTTP_ERROR, resp.statusCode(), resp.body()));
            throw new IllegalStateException(tlUI(new TranslationComponent(Lang.BTN_HTTP_ERROR, resp.statusCode(), resp.body())));
        } else {
            return historyEntities.getLast().getId();
        }
    }
}
