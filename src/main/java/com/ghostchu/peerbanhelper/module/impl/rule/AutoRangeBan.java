package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import inet.ipaddr.IPAddress;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;

@Slf4j
public class AutoRangeBan extends AbstractRuleFeatureModule {
    private int ipv4Prefix;
    private int ipv6Prefix;
    private final Map<PeerAddress, IPAddress> banListMappingCache = Collections.synchronizedMap(new WeakHashMap<>());

    public AutoRangeBan(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
    }

    @Override
    public @NotNull String getName() {
        return "Auto Range Ban";
    }

    @Override
    public @NotNull String getConfigName() {
        return "auto-range-ban";
    }

    @Override
    public boolean needCheckHandshake() {
        return false;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void onEnable() {
        reloadConfig();
        getServer().getWebContainer().javalin()
                .get("/api/modules/" + getConfigName(), this::handleWebAPI, Role.USER_READ);
    }

    private void handleWebAPI(Context ctx) {
        ctx.status(HttpStatus.OK);
        ctx.json(Map.of("ipv4-prefix", ipv4Prefix, "ipv6-prefix", ipv6Prefix));
    }

    @Override
    public void onDisable() {

    }

    private void reloadConfig() {
        this.ipv4Prefix = getConfig().getInt("ipv4");
        this.ipv6Prefix = getConfig().getInt("ipv6");
        banListMappingCache.clear();
    }

    @Override
    public boolean isCheckCacheable() {
        return false;
    }

    @Override
    public @NotNull BanResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        if (peer.getPeerId() == null || peer.getPeerId().isEmpty()) {
            return new BanResult(this, PeerAction.NO_ACTION, "N/A", "Waiting for Bittorrent handshaking.");
        }
        IPAddress peerAddress = peer.getPeerAddress().getAddress().withoutPrefixLength();
        if (peerAddress.isIPv4Convertible()) {
            peerAddress = peerAddress.toIPv4();
        }
        for (PeerAddress bannedPeer : getServer().getBannedPeers().keySet()) {
            IPAddress bannedPeerAddress = banListMappingCache.get(bannedPeer);
            if (bannedPeerAddress == null) {
                IPAddress bannedAddress = bannedPeer.getAddress();
                if (bannedPeer.getAddress().isIPv4()) {
                    bannedAddress = IPAddressUtil.toPrefixBlock(bannedAddress, ipv4Prefix);
                } else {
                    bannedAddress = IPAddressUtil.toPrefixBlock(bannedAddress, ipv6Prefix);
                }
                bannedPeerAddress = bannedAddress;
                banListMappingCache.put(bannedPeer, bannedPeerAddress);
            }
            if (bannedPeerAddress.contains(peerAddress)) {
                return new BanResult(this, PeerAction.BAN, bannedPeerAddress.toString(), String.format(Lang.ARB_BANNED, peerAddress, bannedPeer.getAddress()));
            }
        }
        return new BanResult(this, PeerAction.NO_ACTION, "N/A", "All ok!");
    }


}
