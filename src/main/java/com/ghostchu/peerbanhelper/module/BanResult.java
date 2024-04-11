package com.ghostchu.peerbanhelper.module;

public record BanResult(AbstractFeatureModule moduleContext, PeerAction action, String reason) {
}
