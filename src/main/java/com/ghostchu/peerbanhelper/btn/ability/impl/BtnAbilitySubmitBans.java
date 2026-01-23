package com.ghostchu.peerbanhelper.btn.ability.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ability.AbstractBtnAbility;
import com.ghostchu.peerbanhelper.btn.ping.BtnBan;
import com.ghostchu.peerbanhelper.btn.ping.BtnBanPing;
import com.ghostchu.peerbanhelper.databasent.service.HistoryService;
import com.ghostchu.peerbanhelper.databasent.service.MetadataService;
import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;
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
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class BtnAbilitySubmitBans extends AbstractBtnAbility {
    private final BtnNetwork btnNetwork;
    private final long interval;
    private final String endpoint;
    private final long randomInitialDelay;
    private final MetadataService metadataDao;
    private final HistoryService historyDao;
    private final boolean powCaptcha;

    public BtnAbilitySubmitBans(BtnNetwork btnNetwork, JsonObject ability, MetadataService metadataDao, HistoryService historyDao) {
        this.btnNetwork = btnNetwork;
        this.metadataDao = metadataDao;
        this.historyDao = historyDao;
        this.interval = ability.get("interval").getAsLong();
        this.endpoint = ability.get("endpoint").getAsString();
        this.randomInitialDelay = ability.get("random_initial_delay").getAsLong();
        this.powCaptcha = ability.has("pow_captcha") && ability.get("pow_captcha").getAsBoolean();
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
        btnNetwork.getScheduler().scheduleWithFixedDelay(this::submit, ThreadLocalRandom.current().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    @Override
    public void unload() {
        Main.getEventBus().unregister(this);
    }

    private boolean setMemCursor(long position) {
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
            Page<HistoryEntity> page = new Page<>(1, 100); // 每页处理 100 条
            do {
                var result = historyDao.page(page, new QueryWrapper<HistoryEntity>().gt("id", getMemCursor()));
                setMemCursor(createSubmitRequest(result.getRecords()));
                requests++;
                size += result.getRecords().size();
            } while (page.hasNext());
            log.info(tlUI(Lang.BTN_SUBMITTED_BANS, size, requests));
            setLastStatus(true, new TranslationComponent(Lang.BTN_REPORTED_DATA, size));
        } catch (IllegalStateException ignored) {
            // 子请求已处理报错信息
        } catch (Throwable e) {
            log.error(tlUI(Lang.BTN_UNKNOWN_ERROR), e);
            setLastStatus(false, new TranslationComponent(Lang.BTN_UNKNOWN_ERROR, e.getClass().getName() + ": " + e.getMessage()));
        }
    }

    private long createSubmitRequest(List<HistoryEntity> historyEntities) throws RuntimeException {
        BtnBanPing ping = new BtnBanPing(historyEntities.stream().map(historyEntity -> {
            try {
                return BtnBan.from(historyEntity); // 旧版本数据可能存在空值引发空指针错误，这里处理一下，这种数据就不提交了
            } catch (Exception e) {
                return null;
            }
        }).filter(Objects::nonNull).toList());
        byte[] jsonBytes = JsonUtil.getGson().toJson(ping).getBytes(StandardCharsets.UTF_8);
        RequestBody body = createGzipRequestBody(jsonBytes);
        Request.Builder request = new Request.Builder()
                .url(endpoint)
                .post(body)
                .header("Content-Encoding", "gzip");
        if (powCaptcha) btnNetwork.gatherAndSolveCaptchaBlocking(request, "submit_bans");
        try (Response resp = btnNetwork.getHttpClient().newCall(request.build()).execute()) {
            if (!resp.isSuccessful()) { // 检查2xx状态码
                String responseBody = resp.body() != null ? resp.body().string() : "";
                log.error(tlUI(Lang.BTN_REQUEST_FAILS, resp.code() + " - " + responseBody));
                setLastStatus(false, new TranslationComponent(Lang.BTN_HTTP_ERROR, resp.code(), responseBody));
                throw new IllegalStateException(tlUI(new TranslationComponent(Lang.BTN_HTTP_ERROR, resp.code(), responseBody)));
            } else {
                return historyEntities.getLast().getId();
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
