package com.ghostchu.peerbanhelper.module.impl;

import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.RuleParseHelper;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.util.List;

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
    public BanResult shouldBanPeer(Torrent torrent, Peer peer) {
        List<String> bannedPeers = getConfig().getStringList("banned-peer-id");
        for (String rule : bannedPeers) {
            if(RuleParseHelper.match(peer.getPeerId(), rule)){
                return new BanResult(true, "匹配 PeerId 规则："+rule);
            }
        }
        return new BanResult(false, "无匹配");
    }
}
