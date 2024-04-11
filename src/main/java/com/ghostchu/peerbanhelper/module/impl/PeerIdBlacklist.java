package com.ghostchu.peerbanhelper.module.impl;

import com.ghostchu.peerbanhelper.config.ModuleBaseConfigSection;
import com.ghostchu.peerbanhelper.config.section.ModulePeerIdBlacklistConfigSection;
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

public class PeerIdBlacklist extends AbstractFeatureModule<ModulePeerIdBlacklistConfigSection> {

    public PeerIdBlacklist(ModulePeerIdBlacklistConfigSection section) {
        super(section);
    }

    @Override
    public String getName() {
        return "PeerId Blacklist";
    }

    @Override
    public BanResult shouldBanPeer(Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        List<String> bannedPeers = getConfig().getBannedPeerId();
        for (String rule : bannedPeers) {
            if (RuleParseHelper.match(peer.getPeerId(), rule)) {
                return new BanResult(this,PeerAction.BAN, String.format(Lang.MODULE_PID_MATCH_PEER_ID, rule));
            }
        }
        return new BanResult(this,PeerAction.NO_ACTION, "No matches");
    }

}
