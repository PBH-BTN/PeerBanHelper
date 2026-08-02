package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.BanList;
import com.ghostchu.peerbanhelper.DownloaderServer;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelper;
import com.ghostchu.peerbanhelper.banpipeline.PipelineTask;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.peerbanhelper.wrapper.StructuredData;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import inet.ipaddr.IPAddress;
import inet.ipaddr.ipv4.IPv4Address;
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
    private String teredoMode;
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
        webContainer.routes()
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
        ctx.json(new StdResp(true, null, Map.of("ipv4-prefix", ipv4Prefix, "ipv6-prefix", ipv6Prefix, "teredo", teredoMode)));
    }

    @Override
    public void onDisable() {
        Main.getReloadManager().unregister(this);
    }

    private void reloadConfig() {
        this.ipv4Prefix = getConfig().getInt("ipv4");
        this.ipv6Prefix = getConfig().getInt("ipv6");
        this.teredoMode = getConfig().getString("teredo", "parse");
        this.banDuration = getConfig().getLong("ban-duration", 0);
        getCache().invalidateAll();
    }

    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, @NotNull PipelineTask<?> task) {
        if (isHandShaking(peer)) {
            return pass();
        }
        if (banList.contains(peer.getPeerAddress())) {
            return pass();
        }
        IPAddress peerAddress = peer.getPeerAddress().getAddress().withoutPrefixLength();
        peerAddress = resolveTeredo(peerAddress);
        if (peerAddress == null) {
            return pass();
        }
        if (peerAddress.isIPv4Convertible()) {
            peerAddress = peerAddress.toIPv4();
        }
        AtomicReference<CheckResult> reference = new AtomicReference<>(null);
        IPAddress finalPeerAddress = peerAddress;
        task.setComment(false, "Iterating banList for related ban entries.");
        banList.forEach((bannedAddr, bannedMeta) -> {
            if (reference.get() != null) {
                return;
            }
            if (bannedMeta.isBanForDisconnect()) {
                return;
            }
            IPAddress resolvedAddr = resolveTeredo(bannedAddr);
            if (resolvedAddr == null) {
                return;
            }
            if (finalPeerAddress.isIPv4() != resolvedAddr.isIPv4()) {
                return;
            }
            String addressType = "UNKNOWN";
            IPAddress bannedCidr = resolvedAddr;
            if (resolvedAddr.isIPv4()) {
                addressType = "IPv4/" + ipv4Prefix;
                bannedCidr = resolvedAddr.toPrefixBlock(ipv4Prefix);
            }
            if (resolvedAddr.isIPv6()) {
                addressType = "IPv6/" + ipv6Prefix;
                bannedCidr = resolvedAddr.toPrefixBlock(ipv6Prefix);
            }
            if (bannedCidr.contains(finalPeerAddress)) {
                StructuredData structuredData = StructuredData.create()
                        .add("relatedBannedAddress", bannedAddr.toCompressedString());
                if (isTeredo(bannedAddr)) {
                    structuredData.add("teredoMode", teredoMode)
                            .add("originalBannedAddress", bannedAddr.toCompressedString());
                }
                reference.set(new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(addressType), new TranslationComponent(Lang.ARB_BANNED, finalPeerAddress.toString(),
                        bannedAddr.toString(), bannedCidr.toString(), addressType),
                        structuredData));
            }
        });
        var result = reference.get();
        return Objects.requireNonNullElseGet(result, this::pass);
    }

    /**
     * 根据 teredoMode 配置解析 Teredo 地址。
     * 返回 null 表示应跳过该地址（skip 模式或不可解析的前缀块）；
     * 返回原地址表示非 Teredo 或 original 模式；
     * 返回提取的 IPv4 表示 parse 模式。
     */
    private IPAddress resolveTeredo(IPAddress address) {
        if (!isTeredo(address)) {
            return address;
        }
        return switch (teredoMode) {
            case "skip" -> null;
            case "parse" -> {
                Integer prefixLen = address.getPrefixLength();
                if (address.isMultiple() || (prefixLen != null && prefixLen < 128)) {
                    yield null;
                }
                yield extractTeredoIPv4(address.withoutPrefixLength());
            }
            default -> address;
        };
    }

    private static boolean isTeredo(IPAddress address) {
        return address.isIPv6() && address.toIPv6().isTeredo();
    }

    private static IPAddress extractTeredoIPv4(IPAddress teredoAddress) {
        IPv4Address encodedIPv4 = teredoAddress.toIPv6().getEmbeddedIPv4Address();
        return new IPv4Address(~encodedIPv4.intValue());
    }

}
