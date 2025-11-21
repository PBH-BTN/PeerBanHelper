package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.wrapper.StructuredData;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public final class AntiVampire extends AbstractRuleFeatureModule implements Reloadable {
    private long banDuration;
    private boolean xunleiPreset;

    @Override
    public @NotNull String getName() {
        return "Anti Vampire";
    }

    @Override
    public @NotNull String getConfigName() {
        return "anti-vampire";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void onEnable() {
        reloadConfig();
        Main.getReloadManager().register(this);
    }

    @Override
    public boolean isThreadSafe() {
        return true;
    }

    @Override
    public void onDisable() {
        Main.getReloadManager().unregister(this);
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }

    public void reloadConfig() {
        this.banDuration = getConfig().getLong("ban-duration", 0);
        this.xunleiPreset = getConfig().getBoolean("presets.xunlei.enabled");
    }

    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader) {
        if(xunleiPreset){
            CheckResult checkResult = checkXunleiPreset(torrent, peer, downloader);
            if(checkResult != null) return checkResult;
        }
        return pass();
    }

    private CheckResult checkXunleiPreset(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader) {
        boolean isXunlei = false;
        boolean isXunlei0019 = false;
        if (peer.getPeerId() != null) {
            var peerId = peer.getPeerId().toLowerCase(Locale.ROOT);
            if (peerId.startsWith("-xl")) {
                isXunlei = true;
                if (peerId.startsWith("-xl0019")) {
                    isXunlei0019 = true;
                }
            }
        }
        if (peer.getClientName() != null) {
            var clientName = peer.getClientName().toLowerCase(Locale.ROOT);
            if (clientName.startsWith("xunlei")) {
                isXunlei = true;
                if (clientName.contains("0019") || clientName.contains("0.0.1.9")) {
                    isXunlei0019 = true;
                }
            }
        }
        if(!isXunlei){
            return null; // 不是迅雷客户端的直接过
        }
        if(!isXunlei0019){
            return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.MODULE_ANTI_VAMPIRE_TITLE), new TranslationComponent(Lang.MODULE_ANTI_VAMPIRE_DESCRIPTION_XUNLEI_NON_0019),
                    StructuredData.create().add("xunleiType", "non-0019").add("seeding", torrent.isSeeding()));
        }
        // 是迅雷，也是迅雷 0019的

        // 不允许做种连接
        if(torrent.isSeeding()){
            return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.MODULE_ANTI_VAMPIRE_TITLE), new TranslationComponent(Lang.MODULE_ANTI_VAMPIRE_DESCRIPTION_XUNLEI_0019_SEEDING),
                    StructuredData.create().add("xunleiType", "0019").add("seeding", torrent.isSeeding()));
        }
        return null;
    }

}
