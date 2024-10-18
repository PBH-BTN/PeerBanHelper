package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ping.BtnBan;
import com.ghostchu.peerbanhelper.btn.ping.BtnBanPing;
import com.ghostchu.peerbanhelper.btn.ping.BtnPeer;
import com.ghostchu.peerbanhelper.module.impl.rule.BtnNetworkOnline;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;
import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class BtnAbilitySubmitBans extends AbstractBtnAbility {
    private final BtnNetwork btnNetwork;
    private final long interval;
    private final String endpoint;
    private final long randomInitialDelay;
    private long lastReport = System.currentTimeMillis();

    public BtnAbilitySubmitBans(BtnNetwork btnNetwork, JsonObject ability) {
        this.btnNetwork = btnNetwork;
        this.interval = ability.get("interval").getAsLong();
        this.endpoint = ability.get("endpoint").getAsString();
        this.randomInitialDelay = ability.get("random_initial_delay").getAsLong();
    }

    @Override
    public void load() {
        Main.getEventBus().register(this);
        setLastStatus(true, "No content reported to remote yet");
        btnNetwork.getExecuteService().scheduleWithFixedDelay(this::submit, interval + ThreadLocalRandom.current().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    @Override
    public void unload() {
        Main.getEventBus().unregister(this);
    }


    private void submit() {
        try {
            log.info(tlUI(Lang.BTN_SUBMITTING_BANS));
            List<BtnBan> btnPeers = generateBans();
            if (btnPeers.isEmpty()) {
                setLastStatus(true, "Last report is empty, skipped.");
                lastReport = System.currentTimeMillis();
                return;
            }
            BtnBanPing ping = new BtnBanPing(
                    System.currentTimeMillis(),
                    btnPeers
            );
            Request request = new Request.Builder()
                    .url(endpoint)
                    .post(HTTPUtil.gzipBody(JsonUtil.getGson().toJson(ping).getBytes(StandardCharsets.UTF_8)))
                    .header("Content-Encoding", "gzip")
                    .build();
            HTTPUtil.nonRetryableSend(btnNetwork.getHttpClient(), request)
                    .thenAccept(r -> {
                        if (r.code() != 200) {
                            try {
                                String body = r.body().string();
                                log.error(tlUI(Lang.BTN_REQUEST_FAILS, r.code() + " - " + body));
                                setLastStatus(false, "HTTP Error: " + r.code() + " - " + body);
                            } catch (IOException ignored) {
                                setLastStatus(false, "IO Error");
                            }
                        } else {
                            log.info(tlUI(Lang.BTN_SUBMITTED_BANS, btnPeers.size()));
                            setLastStatus(true, "Reported " + btnPeers.size() + " entries.");
                            lastReport = System.currentTimeMillis();
                        }
                    })
                    .exceptionally(e -> {
                        log.warn(tlUI(Lang.BTN_REQUEST_FAILS), e);
                        setLastStatus(false, e.getClass().getName() + ": " + e.getMessage());
                        return null;
                    });
        } catch (Throwable e) {
            log.error("Unable to submit bans", e);
            setLastStatus(false, "Unknown Error: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private List<BtnBan> generateBans() {
        List<BtnBan> list = new ArrayList<>();
        Map<BtnPeer, BanMetadata> map = new HashMap<>();
        btnNetwork.getServer().getBannedPeers().forEach((pa, meta) -> map.put(BtnPeer.from(meta.getTorrent(), meta.getPeer()), meta));
        for (Map.Entry<BtnPeer, BanMetadata> e : map.entrySet()) {
            if (e.getValue().getBanAt() <= lastReport) {
                continue;
            }
            if (e.getValue().isBanForDisconnect()) {
                continue;
            }
            BtnBan btnBan = new BtnBan();
            btnBan.setBtnBan(e.getValue().getContext().equals(BtnNetworkOnline.class.getName()));
            btnBan.setPeer(e.getKey());
            btnBan.setModule(e.getValue().getContext());
            btnBan.setRule(tl(Main.DEF_LOCALE, e.getValue().getDescription()));
            btnBan.setBanUniqueId(e.getValue().getRandomId().toString());
            list.add(btnBan);
        }
        return list;
    }


}
