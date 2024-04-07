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

public class PeerIdBlacklist extends AbstractFeatureModule {
    public PeerIdBlacklist(YamlConfiguration profile) {
        super(profile);
    }

    @Override
    public String getName() {
        return "PeerId Blacklist";
    }

    @Override
    public String getConfigName() {
        return "peer-id-blacklist";
    }

    @Override
    public BanResult shouldBanPeer(Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        List<String> bannedPeers = getConfig().getStringList("banned-peer-id");
        for (String rule : bannedPeers) {
            if (RuleParseHelper.match(peer.getPeerId(), rule)) {
                return new BanResult(PeerAction.BAN, String.format(Lang.MODULE_PID_MATCH_PEER_ID, rule));
            }
        }
        return new BanResult(PeerAction.NO_ACTION, "No matches");
    }
}
