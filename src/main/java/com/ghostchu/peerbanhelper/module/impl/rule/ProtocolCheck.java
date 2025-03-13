package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

@Component
@IgnoreScan
public final class ProtocolCheck extends AbstractRuleFeatureModule implements Reloadable {
    @Autowired
    private JavalinWebContainer webContainer;
    private long banDuration;
    private boolean bep10ValidatorEnabled;
    private long bep10ValidatorMinimiumTransferred;

    @Override
    public @NotNull String getName() {
        return "Protocol Check";
    }

    @Override
    public @NotNull String getConfigName() {
        return "protocol-check";
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
        this.bep10ValidatorEnabled = getConfig().getBoolean("bep10-validator");
        this.bep10ValidatorMinimiumTransferred = getConfig().getLong("bep10-validator-minimum-transferred");
        getCache().invalidateAll();
    }


    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, @NotNull ExecutorService ruleExecuteExecutor) {
        if (isHandShaking(peer)) {
            return handshaking();
        }
        if (bep10ValidatorEnabled && peer.getUploaded() > bep10ValidatorMinimiumTransferred) {
            if (peer.isClientNameAvailable()) {
                return pass();
            } else {
                return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.MODULE_BEP0010_VALIDATOR), new TranslationComponent(Lang.CLIENT_NOT_COMPLETE_BEP0010_HANDSHAKE));
            }
        }
        //}, true);
        return pass();
    }

}
