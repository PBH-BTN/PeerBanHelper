package com.ghostchu.peerbanhelper.module.impl;

import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.RuleParseHelper;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class ClientNameBlacklist extends AbstractFeatureModule {
    public ClientNameBlacklist(YamlConfiguration profile) {
        super(profile);
    }

    @Override
    public String getName() {
        return "ClientName Blacklist";
    }

    @Override
    public String getConfigName() {
        return "client-name-blacklist";
    }

    @Override
    public BanResult shouldBanPeer(Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        List<String> bannedPeers = getConfig().getStringList("banned-client-name");
        for (String rule : bannedPeers) {
            if (RuleParseHelper.match(peer.getClientName(), rule)) {
                return new BanResult(this,PeerAction.BAN, String.format(Lang.MODULE_CNB_MATCH_CLIENT_NAME, rule));
            }
        }
        return new BanResult(this,PeerAction.NO_ACTION, "No matches");
    }
}
