package com.ghostchu.peerbanhelper.module;

public record MatchResultDetail(FeatureModule moduleContext, PeerState state, String rule, String reason,
                                long expireTime) {
}
