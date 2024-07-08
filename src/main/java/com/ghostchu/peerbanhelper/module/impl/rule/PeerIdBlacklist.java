package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.ghostchu.peerbanhelper.util.rule.RuleMatchResult;
import com.ghostchu.peerbanhelper.util.rule.RuleParser;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Component
public class PeerIdBlacklist extends AbstractRuleFeatureModule {
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
    }

    @Override
    public boolean isThreadSafe() {
        return true;
    }

    private void handleWebAPI(Context ctx) {
        ctx.status(HttpStatus.OK);
        ctx.json(Map.of("peerId", bannedPeers.stream().map(Rule::toPrintableText).toList()));
    }

    @Override
    public void onDisable() {

    }

    public void reloadConfig() {
        this.banDuration = getConfig().getLong("ban-duration", 0);
        this.bannedPeers = RuleParser.parse(getConfig().getStringList("banned-peer-id"));
    }


    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        if (isHandShaking(peer) && (peer.getPeerId() == null || peer.getPeerId().isBlank())) {
            return handshaking();
        }
        //return getCache().readCache(this, peer.getPeerId(), () -> {
        RuleMatchResult matchResult = RuleParser.matchRule(bannedPeers, peer.getPeerId());
        if (matchResult.hit()) {
            return new CheckResult(getClass(), PeerAction.BAN, banDuration, matchResult.rule().toString(), String.format(Lang.MODULE_PID_MATCH_PEER_ID, matchResult.rule()));
        }
        return pass();
        //}, true);

    }

}
