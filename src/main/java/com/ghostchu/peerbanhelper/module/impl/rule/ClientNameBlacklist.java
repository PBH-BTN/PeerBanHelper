package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.ghostchu.peerbanhelper.util.rule.RuleMatchResult;
import com.ghostchu.peerbanhelper.util.rule.RuleParser;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Getter
@Component
public class ClientNameBlacklist extends AbstractRuleFeatureModule {
    private List<Rule> bannedPeers;
    @Autowired
    private JavalinWebContainer webContainer;
    private long banDuration;

    @Override
    public @NotNull String getName() {
        return "ClientName Blacklist";
    }

    @Override
    public @NotNull String getConfigName() {
        return "client-name-blacklist";
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
        String locale = locale(ctx);
        ctx.json(Map.of("clientName", bannedPeers.stream().map(r -> r.toPrintableText(locale)).toList()));
    }

    @Override
    public void onDisable() {

    }

    private void reloadConfig() {
        this.bannedPeers = RuleParser.parse(getConfig().getStringList("banned-client-name"));
        this.banDuration = getConfig().getLong("ban-duration", 0);
    }

    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        if (isHandShaking(peer) && (peer.getClientName() == null || peer.getClientName().isBlank())) {
            return handshaking();
        }
        //return getCache().readCache(this, peer.getClientName(), () -> {
        RuleMatchResult matchResult = RuleParser.matchRule(bannedPeers, peer.getClientName());
        if (matchResult.hit()) {
            return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(matchResult.rule().toString()), new TranslationComponent(Lang.MODULE_CNB_MATCH_CLIENT_NAME, String.valueOf(matchResult.rule())));
        }
        return pass();
        //}, true);
    }


}
