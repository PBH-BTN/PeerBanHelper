package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import inet.ipaddr.IPAddress;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ExecutorService;

@Slf4j
@Component
@IgnoreScan
public final class AutoRangeBan extends AbstractRuleFeatureModule implements Reloadable {
    @Autowired
    private PeerBanHelperServer peerBanHelperServer;
    private int ipv4Prefix;
    private int ipv6Prefix;
    @Autowired
    private JavalinWebContainer webContainer;
    private long banDuration;

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
        Main.getReloadManager().register(this);
    }

    @Override
    public boolean isThreadSafe() {
        return super.isThreadSafe();
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }

    private void handleWebAPI(Context ctx) {
        ctx.json(new StdResp(true, null, Map.of("ipv4-prefix", ipv4Prefix, "ipv6-prefix", ipv6Prefix)));
    }

    @Override
    public void onDisable() {
        Main.getReloadManager().unregister(this);
    }

    private void reloadConfig() {
        this.ipv4Prefix = getConfig().getInt("ipv4");
        this.ipv6Prefix = getConfig().getInt("ipv6");
        this.banDuration = getConfig().getLong("ban-duration", 0);
        getCache().invalidateAll();
    }

    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, @NotNull ExecutorService ruleExecuteExecutor) {
        if (isHandShaking(peer)) {
            return pass();
        }
        if (getServer().getBannedPeersDirect().containsKey(peer.getPeerAddress())) {
            return pass();
        }
        IPAddress peerAddress = peer.getPeerAddress().getAddress().withoutPrefixLength();
        if (peerAddress.isIPv4Convertible()) {
            peerAddress = peerAddress.toIPv4();
        }
        for (Map.Entry<PeerAddress, BanMetadata> bannedPeerEntry : getServer().getBannedPeersDirect().entrySet()) {
            if (bannedPeerEntry.getValue().isBanForDisconnect()) {
                continue;
            }
            PeerAddress bannedPeer = bannedPeerEntry.getKey();
            IPAddress bannedAddress = bannedPeer.getAddress().withoutPrefixLength();
            if (bannedAddress.isIPv4Convertible()) {
                bannedAddress = bannedAddress.toIPv4();
            }
            if (peerAddress.isIPv4() != bannedAddress.isIPv4()) {
                continue;
            }
            String addressType = "UNKNOWN";
            if (bannedAddress.isIPv4()) {
                addressType = "IPv4/" + ipv4Prefix;
                bannedAddress = IPAddressUtil.toPrefixBlock(bannedAddress, ipv4Prefix);
            }
            if (bannedAddress.isIPv6()) {
                addressType = "IPv6/" + ipv6Prefix;
                bannedAddress = IPAddressUtil.toPrefixBlock(bannedAddress, ipv6Prefix);
            }
            if (bannedAddress.contains(peerAddress)) {
                return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(addressType), new TranslationComponent(Lang.ARB_BANNED, peerAddress.toString(), bannedPeer.getAddress().toString(), bannedAddress.toString(), addressType));
            }
        }
        return pass();
    }


}
