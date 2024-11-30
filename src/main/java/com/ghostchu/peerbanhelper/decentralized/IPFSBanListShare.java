package com.ghostchu.peerbanhelper.decentralized;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.impl.rule.IPBlackRuleList;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.ReloadStatus;
import com.ghostchu.simplereloadlib.Reloadable;
import io.ipfs.api.IPFS;
import io.ipfs.api.KeyInfo;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public class IPFSBanListShare implements Reloadable {
    private final PeerBanHelperServer peerBanHelperServer;
    @Nullable
    private final IPFS ipfs;
    @Getter
    private final String banlistId;
    private ScheduledExecutorService sched;

    public IPFSBanListShare(DecentralizedManager manager, PeerBanHelperServer peerBanHelperServer) throws IOException {
        this.peerBanHelperServer = peerBanHelperServer;
        this.ipfs = manager.getIpfs();
        var pbhId = Main.getMainConfig().getString("installation-id");
        this.banlistId = "peerbanhelper-banlist-" + pbhId;
        Main.getReloadManager().register(this);
        reloadConfig();
    }

    public void reloadConfig() {
        long interval = Main.getMainConfig().getLong("decentralized.features.publish-banlist.interval", 3600000);
        if (sched != null) {
            sched.shutdown();
        }
        if (interval > 0) {
            sched = Executors.newScheduledThreadPool(4, Thread.ofVirtual().factory());
            sched.scheduleWithFixedDelay(this::publishUpdate, interval, interval, TimeUnit.MILLISECONDS);
        } else {
            sched = null;
        }
    }

    public void publishUpdate() {
        if(this.ipfs == null) return;
        try {
            StringJoiner joiner = new StringJoiner("\n");
            joiner.add("# Generated at " + new Date());
            for (Map.Entry<PeerAddress, BanMetadata> entry : peerBanHelperServer.getBannedPeers().entrySet()) {
                if (!entry.getValue().getContext().equals(IPBlackRuleList.class.getName())) {
                    joiner.add("# BanAt=" + entry.getValue().getBanAt() + " UnBanAt=" + entry.getValue().getUnbanAt() + " Context=" + entry.getValue().getContext() + " Rule=" + tlUI(entry.getValue().getRule()) + " Description=" + tlUI(entry.getValue().getDescription()));
                    joiner.add(entry.getKey().getIp());
                }
            }

            MerkleNode node = ipfs.add(new NamedStreamable.ByteArrayWrapper(Optional.of("PeerBanHelper Banlist"), joiner.toString().getBytes(StandardCharsets.UTF_8))).get(0);
            KeyInfo publishKey = null;
            for (KeyInfo keyInfo : ipfs.key.list()) {
                if (keyInfo.name.equals(banlistId)) {
                    publishKey = keyInfo;
                    break;
                }
            }
            if (publishKey == null) {
                publishKey = ipfs.key.gen(banlistId, Optional.empty(), Optional.empty());
            }
            KeyInfo finalPublishKey = publishKey;
            Thread.startVirtualThread(() -> {
                try {
                    var map = ipfs.name.publish(node.hash, Optional.of(finalPublishKey.name));
                    log.info(tlUI(Lang.IPFS_BANLIST_PUBLISHED));
                } catch (IOException ignored) {
                }
            });
        } catch (Exception e) {
            log.error("Unable to publish/republish the banlist to IPFS", e);
        }
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return new ReloadResult(ReloadStatus.SUCCESS, null, null);
    }
}
