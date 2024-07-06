package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import inet.ipaddr.IPAddress;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;

@Slf4j
@Component
public class AutoRangeBan extends AbstractRuleFeatureModule {
    private final Map<PeerAddress, IPAddress> banListMappingCache = Collections.synchronizedMap(new WeakHashMap<>());
    @Autowired
    private PeerBanHelperServer peerBanHelperServer;
    private int ipv4Prefix;
    private int ipv6Prefix;
    @Autowired
    private JavalinWebContainer webContainer;
    @Override
    public @NotNull String getName() {
        return "Auto Range Ban";
    }

    @Override
    public @NotNull String getConfigName() {
        return "auto-range-ban";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void onEnable() {
        reloadConfig();
        webContainer.javalin()
                .get("/api/modules/" + getConfigName(), this::handleWebAPI, Role.USER_READ);
    }

    @Override
    public boolean isThreadSafe() {
        return true;
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
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        IPAddress peerAddress = peer.getPeerAddress().getAddress().withoutPrefixLength();
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
                return new CheckResult(getClass(), PeerAction.BAN, bannedPeerAddress.toString(), String.format(Lang.ARB_BANNED, peerAddress, bannedPeer.getAddress()));
            }
        }
        return pass();
    }


}
