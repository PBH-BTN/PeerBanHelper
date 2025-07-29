package com.ghostchu.peerbanhelper.util.traversal.btstun;

import com.cdnbye.core.nat.NatType;
import com.cdnbye.core.nat.StunClient;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.util.PBHPortMapper;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.*;

@Slf4j
@Component
public class StunManager {
    private final Map<Downloader, BTStunInstance> perDownloaderStun = Collections.synchronizedMap(new HashMap<>());
    private final PBHPortMapper portMapper;
    private NatType cachedNatType = NatType.Unknown;

    public StunManager(PBHPortMapper portMapper) {
        this.portMapper = portMapper;
        Thread.ofVirtual().name("StunManager-RefreshNatType").start(this::refreshNatType);
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
                log.warn("Invalid STUN server format: {}, skipping", stunServer);
                continue;
            }
            try {
                NatType natType = StunClient.query(stun[0], Integer.parseInt(stun[1]), InetAddress.getLocalHost().getHostAddress()).getNatType();
                if (natType != NatType.Unknown) {
                    this.cachedNatType = natType;
                    log.debug("Successfully determined NAT type {} using server {}", natType, stunServer);
                    return cachedNatType;
                }
                log.warn("STUN server {} returned Unknown NAT type, trying next server", stunServer);
            } catch (Exception e) {
                log.warn("Failed to query STUN server {}: {}, trying next server", stunServer, e.getMessage());
            }
        }
        log.error("All STUN servers failed or returned Unknown NAT type");
        this.cachedNatType = NatType.Unknown;
        return cachedNatType;
    }
}
