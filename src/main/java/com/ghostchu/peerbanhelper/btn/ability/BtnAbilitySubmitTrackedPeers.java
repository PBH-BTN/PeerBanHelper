package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ping.BtnTrackedPeer;
import com.ghostchu.peerbanhelper.btn.ping.BtnTrackedPeerPing;
import com.ghostchu.peerbanhelper.database.dao.impl.tmp.TrackedPeersDao;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.paging.Pageable;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.gson.JsonObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class BtnAbilitySubmitTrackedPeers extends AbstractBtnAbility {
    private final BtnNetwork btnNetwork;
    private final long interval;
    private final String endpoint;
    private final long randomInitialDelay;
    private final TrackedPeersDao trackedPeersDao;
    private long lastSubmitId;
    private final Lock lock = new ReentrantLock();

    public BtnAbilitySubmitTrackedPeers(BtnNetwork btnNetwork, JsonObject ability, TrackedPeersDao trackedPeersDao) {
        this.btnNetwork = btnNetwork;
        this.trackedPeersDao = trackedPeersDao;
        this.interval = ability.get("interval").getAsLong();
        this.endpoint = ability.get("endpoint").getAsString();
        this.randomInitialDelay = ability.get("random_initial_delay").getAsLong();
    }


    @Override
    public String getName() {
        return "BtnAbilitySubmitTrackedPeers";
    }

    @Override
    public TranslationComponent getDisplayName() {
        return new TranslationComponent(Lang.BTN_ABILITY_SUBMIT_TRACKED_PEERS);
    }

    @Override
    public TranslationComponent getDescription() {
        return new TranslationComponent(Lang.BTN_ABILITY_SUBMIT_TRACKED_PEERS_DESCRIPTION);
    }

    @Override
    public void load() {
        setLastStatus(true, new TranslationComponent(Lang.BTN_NO_CONTENT_REPORTED_YET));
        btnNetwork.getExecuteService().scheduleWithFixedDelay(this::submit, interval + ThreadLocalRandom.current().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    private void submit() {
        if (!lock.tryLock()) {
            return; // this is possible since we can send multiple requests
        }
        try {
            log.info(tlUI(Lang.BTN_SUBMITTING_TRACKED_PEERS));
            List<BtnTrackedPeer> btnPeers = generatePing(lastSubmitId);
            while (!btnPeers.isEmpty()) {
                BtnTrackedPeerPing ping = new BtnTrackedPeerPing(
                        btnPeers
                );
                MutableRequest request = MutableRequest.POST(endpoint
                        , HTTPUtil.gzipBody(JsonUtil.getGson().toJson(ping).getBytes(StandardCharsets.UTF_8))
                ).header("Content-Encoding", "gzip");
                HTTPUtil.nonRetryableSend(btnNetwork.getHttpClient(), request, HttpResponse.BodyHandlers.ofString())
                        .orTimeout(30, TimeUnit.SECONDS)
                        .thenAccept(r -> {
                            if (r.statusCode() != 200) {
                                log.error(tlUI(Lang.BTN_REQUEST_FAILS, r.statusCode() + " - " + r.body()));
                                setLastStatus(false, new TranslationComponent(Lang.BTN_HTTP_ERROR, r.statusCode(), r.body()));
                            } else {
                                log.info(tlUI(Lang.BTN_SUBMITTED_TRACKED_PEERS, ping.getTrackedPeers().size()));
                                setLastStatus(true, new TranslationComponent(Lang.BTN_REPORTED_DATA, ping.getTrackedPeers().size()));
                            }
                        })
                        .exceptionally(e -> {
                            log.warn(tlUI(Lang.BTN_REQUEST_FAILS), e);
                            setLastStatus(false, new TranslationComponent(e.getClass().getName() + ": " + e.getMessage()));
                            return null;
                        })
                        .join();
                lastSubmitId = btnPeers.getLast().getId();
            }

        } catch (Throwable e) {
            log.error("Unable to sync tracked peers", e);
            setLastStatus(false, new TranslationComponent("Unknown Error: " + e.getClass().getName() + ": " + e.getMessage()));
        } finally {
            lock.unlock();
        }
    }


    @SneakyThrows
    private List<BtnTrackedPeer> generatePing(long lastSubmitId) {
        Pageable pageable = new Pageable(0, 1000);
        return trackedPeersDao.getPendingSubmitTrackedPeers(pageable, lastSubmitId).getResults().stream()
                .map(BtnTrackedPeer::from).collect(Collectors.toList());
    }

    @Override
    public void unload() {

    }


}
