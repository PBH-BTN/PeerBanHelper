package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.RuleParseHelper;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class PeerIdBlacklist extends AbstractFeatureModule {
    private List<String> bannedPeerId;

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
    public void onEnable() {
        reloadConfig();
    }

    @Override
    public void onDisable() {

    }

    public void reloadConfig() {
        this.bannedPeerId = getConfig().getStringList("banned-peer-id");
    }


    @Override
    public @NotNull BanResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        RuleParseHelper.MatchResult map = RuleParseHelper.matchMultiple(peer.getPeerId(),  bannedPeerId);
        if (map.result()) {
            return new BanResult(this, PeerAction.BAN, String.format(Lang.MODULE_PID_MATCH_PEER_ID, map.hitRule()));
        }
        return new BanResult(this, PeerAction.NO_ACTION, "No matches");
    }


}
