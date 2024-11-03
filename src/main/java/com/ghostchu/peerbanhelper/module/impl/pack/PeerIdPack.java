package com.ghostchu.peerbanhelper.module.impl.pack;

import com.ghostchu.peerbanhelper.util.rule.Rule;

import java.util.List;

public record PeerIdPack (
        List<Rule> bannedPeers
){
}
