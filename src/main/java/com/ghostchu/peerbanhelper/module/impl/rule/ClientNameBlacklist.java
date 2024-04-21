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

public class ClientNameBlacklist extends AbstractFeatureModule {
    private List<String> bannedPeers;
    private List<String> excludePeers;

    public ClientNameBlacklist(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
    }

    @Override
    public @NotNull String getName() {
        return "ClientName Blacklist";
    }

    @Override
    public @NotNull String getConfigName() {
        return "client-name-blacklist";
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

    private void reloadConfig() {
        this.bannedPeers = getConfig().getStringList("banned-client-name");
        this.bannedPeers = getConfig().getStringList("exclude-client-name");
    }

    @Override
    public @NotNull BanResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        for (String rule : excludePeers) {
            if (RuleParseHelper.match(peer.getClientName(), rule)) {
                return new BanResult(this, PeerAction.SKIP, "skip: " + rule);
            }
        }
        for (String rule : bannedPeers) {
            if (RuleParseHelper.match(peer.getClientName(), rule)) {
                return new BanResult(this, PeerAction.BAN, String.format(Lang.MODULE_CNB_MATCH_CLIENT_NAME, rule));
            }
        }
        return new BanResult(this, PeerAction.NO_ACTION, "No matches");
    }


}
