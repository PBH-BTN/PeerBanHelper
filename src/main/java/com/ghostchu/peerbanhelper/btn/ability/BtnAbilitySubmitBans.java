package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ping.BtnBan;
import com.ghostchu.peerbanhelper.btn.ping.BtnBanPing;
import com.ghostchu.peerbanhelper.btn.ping.BtnPeer;
import com.ghostchu.peerbanhelper.event.PeerBanEvent;
import com.ghostchu.peerbanhelper.module.impl.rule.BtnNetworkOnline;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.common.eventbus.Subscribe;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BtnAbilitySubmitBans implements BtnAbility {
    private final BtnNetwork btnNetwork;
    private final long interval;
    private final String endpoint;
    private final long randomInitialDelay;
    private final Map<BtnPeer, BanMetadata> bans = new ConcurrentHashMap<>();
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
        btnNetwork.getExecuteService().scheduleAtFixedRate(this::submit, interval + new Random().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    @Override
    public void unload() {
        Main.getEventBus().unregister(this);
    }

    @Subscribe
    public void onPeerBanEvent(PeerBanEvent event) {
        bans.put(BtnPeer.from(event.getTorrentObj(), event.getPeerObj()), event.getBanMetadata());
    }

    private void submit() {
        log.info(Lang.BTN_SUBMITTING_BANS);
        List<BtnBan> btnPeers = generateBans();
        BtnBanPing ping = new BtnBanPing(
                System.currentTimeMillis(),
                btnPeers
        );
        MutableRequest request = MutableRequest.POST(endpoint
                , HTTPUtil.gzipBody(JsonUtil.getGson().toJson(ping).getBytes(StandardCharsets.UTF_8))
        ).header("Content-Encoding", "gzip");
        HTTPUtil.retryableSend(btnNetwork.getHttpClient(), request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(r -> {
                    if (r.statusCode() != 200) {
                        log.warn(Lang.BTN_REQUEST_FAILS, r.statusCode() + " - " + r.body());
                    } else {
                        log.info(Lang.BTN_SUBMITTED_BANS, btnPeers.size());
                    }
                })
                .exceptionally(e -> {
                    log.warn(Lang.BTN_REQUEST_FAILS, e);
                    return null;
                });
    }

    private List<BtnBan> generateBans() {
        List<BtnBan> list = new ArrayList<>();
        for (Map.Entry<BtnPeer, BanMetadata> e : bans.entrySet()) {
            if (e.getValue().getBanAt() <= lastReport) {
                continue;
            }
            BtnBan btnBan = new BtnBan();
            btnBan.setBtnBan(e.getValue().getContext().equals(BtnNetworkOnline.class.getName()));
            btnBan.setPeer(e.getKey());
            btnBan.setModule(e.getValue().getContext());
            btnBan.setRule(e.getValue().getDescription());
            btnBan.setBanUniqueId(e.getValue().getRandomId().toString());
            list.add(btnBan);
        }
        lastReport = System.currentTimeMillis();
        return list;
    }


}
