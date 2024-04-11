package com.ghostchu.peerbanhelper.module.impl;

import com.ghostchu.peerbanhelper.config.ModuleBaseConfigSection;
import com.ghostchu.peerbanhelper.config.section.ModuleClientNameBlacklistConfigSection;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.RuleParseHelper;

import java.util.concurrent.ExecutorService;

public class ClientNameBlacklist extends AbstractFeatureModule<ModuleClientNameBlacklistConfigSection> {

    public ClientNameBlacklist(ModuleBaseConfigSection section) {
        super(section);
    }

    @Override
    public String getName() {
        return "ClientName Blacklist";
    }

    @Override
    public BanResult shouldBanPeer(Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        for (String rule : getConfig().getBannedClientName()) {
            if (RuleParseHelper.match(peer.getClientName(), rule)) {
                return new BanResult(this,PeerAction.BAN, String.format(Lang.MODULE_CNB_MATCH_CLIENT_NAME, rule));
            }
        }
        return new BanResult(this,PeerAction.NO_ACTION, "No matches");
    }

}
