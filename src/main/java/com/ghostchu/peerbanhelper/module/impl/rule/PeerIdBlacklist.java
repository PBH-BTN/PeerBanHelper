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
import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.ghostchu.peerbanhelper.util.rule.RuleMatchResult;
import com.ghostchu.peerbanhelper.util.rule.RuleParser;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Component
@IgnoreScan
public final class PeerIdBlacklist extends AbstractRuleFeatureModule implements Reloadable {
    private List<Rule> bannedPeers;
    @Autowired
    private JavalinWebContainer webContainer;
    private long banDuration;

    @Override
    public @NotNull String getName() {
        return "PeerId Blacklist";
    }

    @Override
    public @NotNull String getConfigName() {
        return "peer-id-blacklist";
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
        return true;
    }

    private void handleWebAPI(Context ctx) {
        String locale = locale(ctx);
        ctx.json(new StdResp(true, null, Map.of("peerId", bannedPeers.stream().map(r -> r.toPrintableText(locale)).toList())));
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
        this.bannedPeers = RuleParser.parse(getConfig().getStringList("banned-peer-id"));
        getCache().invalidateAll();
    }


    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, @NotNull ExecutorService ruleExecuteExecutor) {
        if (isHandShaking(peer) && (peer.getPeerId() == null || peer.getPeerId().isBlank())) {
            return handshaking();
        }
        //return getCache().readCache(this, peer.getPeerId(), () -> {
        RuleMatchResult matchResult = RuleParser.matchRule(bannedPeers, peer.getPeerId());
        if (matchResult.hit()) {
            return new CheckResult(getClass(), PeerAction.BAN, banDuration, matchResult.rule().matcherName(), new TranslationComponent(Lang.MODULE_CNB_MATCH_CLIENT_NAME, matchResult.comment()));
        }
        return pass();
        //}, true);

    }

}
