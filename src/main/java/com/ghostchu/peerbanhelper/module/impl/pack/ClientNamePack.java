package com.ghostchu.peerbanhelper.module.impl.pack;

import com.ghostchu.peerbanhelper.util.rule.Rule;

import java.util.List;

public record ClientNamePack(
        List<Rule> bannedPeers
){
}
