package com.ghostchu.peerbanhelper.module;

public record BanResult(FeatureModule moduleContext, PeerAction action, String reason) {
}
