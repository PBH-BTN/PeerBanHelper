package com.ghostchu.peerbanhelper.btn.ability.impl.legacy;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ability.AbstractBtnAbility;
import com.ghostchu.peerbanhelper.btn.ping.legacy.LegacyBtnBan;
import com.ghostchu.peerbanhelper.btn.ping.legacy.LegacyBtnBanPing;
import com.ghostchu.peerbanhelper.btn.ping.legacy.LegacyBtnPeer;
import com.ghostchu.peerbanhelper.module.impl.rule.BtnNetworkOnline;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.google.common.hash.Hashing;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;
import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class LegacyBtnAbilitySubmitBans extends AbstractBtnAbility {
    private final BtnNetwork btnNetwork;
    private final long interval;
    private final String endpoint;
    private final long randomInitialDelay;
    private final HTTPUtil httpUtil;
    private long lastReport = System.currentTimeMillis();

    public LegacyBtnAbilitySubmitBans(BtnNetwork btnNetwork, HTTPUtil httpUtil, JsonObject ability) {
        this.btnNetwork = btnNetwork;
        this.httpUtil = httpUtil;
        this.interval = ability.get("interval").getAsLong();
        this.endpoint = ability.get("endpoint").getAsString();
        this.randomInitialDelay = ability.get("random_initial_delay").getAsLong();
    }

    @Override
    public String getName() {
        return "BtnAbilitySubmitBans (Legacy)";
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
        btnNetwork.getScheduler().scheduleWithFixedDelay(this::submit, interval + ThreadLocalRandom.current().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    @Override
    public void unload() {
        Main.getEventBus().unregister(this);
    }


    private void submit() {
        try {
            log.info(tlUI(Lang.BTN_SUBMITTING_BANS));
            List<LegacyBtnBan> btnPeers = generateBans();
            if (btnPeers.isEmpty()) {
                setLastStatus(true, new TranslationComponent(Lang.BTN_LAST_REPORT_EMPTY));
                lastReport = System.currentTimeMillis();
                return;
            }
            LegacyBtnBanPing ping = new LegacyBtnBanPing(
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
                if (response.code() != 200) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    log.error(tlUI(Lang.BTN_REQUEST_FAILS, response.code() + " - " + responseBody));
                    setLastStatus(false, new TranslationComponent(Lang.BTN_HTTP_ERROR, response.code(), responseBody));
                } else {
                    log.info(tlUI(Lang.BTN_SUBMITTED_BANS, btnPeers.size()));
                    setLastStatus(true, new TranslationComponent(Lang.BTN_REPORTED_DATA, btnPeers.size()));
                    lastReport = System.currentTimeMillis();
                }
            } catch (Exception e) {
                log.warn(tlUI(Lang.BTN_REQUEST_FAILS), e);
                setLastStatus(false, new TranslationComponent(e.getClass().getName() + ": " + e.getMessage()));
            }
        } catch (Throwable e) {
            log.error(tlUI(Lang.BTN_SUBMITTED_BANS), e);
            setLastStatus(false, new TranslationComponent(Lang.BTN_UNKNOWN_ERROR, e.getClass().getName() + ": " + e.getMessage()));
        }
    }

    private List<LegacyBtnBan> generateBans() {
        List<LegacyBtnBan> list = new ArrayList<>();
        Map<LegacyBtnPeer, BanMetadata> map = new HashMap<>();
        btnNetwork.getServer().getBannedPeers().forEach((pa, meta) -> map.put(LegacyBtnPeer.from(meta.getTorrent(), meta.getPeer()), meta));
        for (Map.Entry<LegacyBtnPeer, BanMetadata> e : map.entrySet()) {
            if (e.getValue().getBanAt() <= lastReport) {
                continue;
            }
            if (e.getValue().isBanForDisconnect()) {
                continue;
            }
            LegacyBtnBan btnBan = new LegacyBtnBan();
            btnBan.setBtnBan(e.getValue().getContext().equals(BtnNetworkOnline.class.getName()));
            btnBan.setPeer(e.getKey());
            btnBan.setModule(e.getValue().getContext());
            btnBan.setRule(tl(Main.DEF_LOCALE, e.getValue().getDescription()));
            btnBan.setBanUniqueId(Hashing.sha256().hashString(e.getValue().toString(), StandardCharsets.UTF_8).toString());
            list.add(btnBan);
        }
        return list;
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