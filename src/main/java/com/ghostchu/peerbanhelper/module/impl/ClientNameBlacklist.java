package com.ghostchu.peerbanhelper.module.impl;

import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.RuleParseHelper;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.util.List;

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
    public BanResult shouldBanPeer(Torrent torrent, Peer peer) {
        List<String> bannedPeers = getConfig().getStringList("banned-client-name");
        for (String rule : bannedPeers) {
            if(RuleParseHelper.match(peer.getClientName(), rule)){
                return new BanResult(true, "匹配 ClientName 规则："+rule);
            }
        }
        return new BanResult(false, "无匹配");
    }
}
