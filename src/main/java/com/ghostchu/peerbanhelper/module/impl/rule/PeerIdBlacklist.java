package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.ghostchu.peerbanhelper.util.rule.RuleMatchResult;
import com.ghostchu.peerbanhelper.util.rule.RuleParser;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.Getter;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Getter
public class PeerIdBlacklist extends AbstractRuleFeatureModule {
    private List<Rule> bannedPeers;

    public PeerIdBlacklist(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
    }

    @Override
    public @NotNull String getName() {
        return "PeerId Blacklist";
    }

    @Override
    public @NotNull String getConfigName() {
        return "peer-id-blacklist";
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
    public boolean isCheckCacheable() {
        return true;
    }



    @Override
    public void onEnable() {
        reloadConfig();
        getServer().getWebContainer().javalin()
                .get("/api/modules/" + getConfigName(), this::handleWebAPI);
    }

    private void handleWebAPI(Context ctx) {
        ctx.status(HttpStatus.OK);
        ctx.json(Map.of("peerId", bannedPeers.stream().map(Rule::toPrintableText).toList()));
    }

    @Override
    public void onDisable() {

    }

    public void reloadConfig() {
        this.bannedPeers = RuleParser.parse(getConfig().getStringList("banned-peer-id"));
    }


    @Override
    public @NotNull BanResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        RuleMatchResult matchResult = RuleParser.matchRule(bannedPeers, peer.getPeerId());
        if (matchResult.hit()) {
            return new BanResult(this, PeerAction.BAN, matchResult.rule().toString(), String.format(Lang.MODULE_PID_MATCH_PEER_ID, matchResult.rule()));
        }
        return new BanResult(this, PeerAction.NO_ACTION, "N/A", "No matches");
    }

}
