package com.ghostchu.peerbanhelper.util.traversal.btstun;

import com.cdnbye.core.nat.NatType;
import com.cdnbye.core.nat.NettyStunClient;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public class StunManager implements AutoCloseable {
    private NatType cachedNatType = NatType.Unknown;
    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());

    public StunManager() {
        scheduledExecutorService.scheduleWithFixedDelay(this::refreshNatType, 0, 1, TimeUnit.HOURS);
    }

    @NotNull
    public NatType getCachedNatType() {
        return cachedNatType;
    }

    @NotNull
    public NatType refreshNatType() {
        List<String> stunServers = Main.getMainConfig().getStringList("stun.udp-servers");
        List<String> shuffledServers = new ArrayList<>(stunServers);
        Collections.shuffle(shuffledServers);

        for (String stunServer : shuffledServers) {
            String[] stun = stunServer.split(":");
            if (stun.length != 2) {
                log.debug("Invalid STUN server format: {}, skipping", stunServer);
                continue;
            }
            try {
                NatType natType = NettyStunClient.query(stun[0], Integer.parseInt(stun[1]), "0.0.0.0")
                        .natType();
                if (natType != NatType.Unknown && natType != NatType.UdpBlocked) {
                    this.cachedNatType = natType;
                    log.debug("Successfully determined NAT type {} using server {}", natType, stunServer);
                    return cachedNatType;
                }
                log.debug("STUN server {} returned Unknown NAT type, trying next server", stunServer);
            } catch (Exception e) {
                log.debug("Failed to query STUN server {}: {}, trying next server", stunServer, e.getMessage());
            }
        }
        log.error(tlUI(Lang.AUTOSTUN_STUN_SERVICE_UNAVAILABLE));
        this.cachedNatType = NatType.UdpBlocked;
        return cachedNatType;
    }

    @Override
    public void close() {
        if (!this.scheduledExecutorService.isShutdown()) {
            this.scheduledExecutorService.shutdown();
        }
    }
}
