package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.BanList;
import com.ghostchu.peerbanhelper.DownloaderServer;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelper;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.peerbanhelper.wrapper.StructuredData;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import inet.ipaddr.IPAddress;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public final class AutoRangeBan extends AbstractRuleFeatureModule implements Reloadable {
    @Autowired
    private PeerBanHelper peerBanHelper;
    private int ipv4Prefix;
    private int ipv6Prefix;
    @Autowired
    private JavalinWebContainer webContainer;
    private long banDuration;
    @Autowired
    private DownloaderServer downloaderServer;
    @Autowired
    private BanList banList;

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
        webContainer.javalin().unsafe.routes
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
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader) {
        if (isHandShaking(peer)) {
            return pass();
        }
        if (banList.contains(peer.getPeerAddress())) {
            return pass();
        }
        IPAddress peerAddress = peer.getPeerAddress().getAddress().withoutPrefixLength();
        if (peerAddress.isIPv4Convertible()) {
            peerAddress = peerAddress.toIPv4();
        }
        AtomicReference<CheckResult> reference = new AtomicReference<>(null);
        IPAddress finalPeerAddress = peerAddress;
        banList.forEach((bannedAddr, bannedMeta) -> {
            if (reference.get() != null) {
                return;
            }
            if (bannedMeta.isBanForDisconnect()) {
                return;
            }
            if (finalPeerAddress.isIPv4() != bannedAddr.isIPv4()) {
                return;
            }
            String addressType = "UNKNOWN";
            if (bannedAddr.isIPv4()) {
                addressType = "IPv4/" + ipv4Prefix;
                bannedAddr = IPAddressUtil.toPrefixBlockAndZeroHost(bannedAddr, ipv4Prefix);
            }
            if (bannedAddr.isIPv6()) {
                addressType = "IPv6/" + ipv6Prefix;
                bannedAddr = IPAddressUtil.toPrefixBlockAndZeroHost(bannedAddr, ipv6Prefix);
            }
            if (bannedAddr.contains(finalPeerAddress)) {
                reference.set(new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(addressType), new TranslationComponent(Lang.ARB_BANNED, finalPeerAddress.toString(),
                        finalPeerAddress.toString(), bannedAddr.toString(), addressType),
                        StructuredData.create().add("relatedBannedAddress", bannedAddr.toNormalizedString())));
            }
        });
        var result = reference.get();
        return Objects.requireNonNullElseGet(result, this::pass);
    }


}
