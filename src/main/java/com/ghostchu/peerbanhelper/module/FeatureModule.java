package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

public interface FeatureModule {
    String getName();
    String getConfigName();
    boolean isModuleEnabled();
    BanResult shouldBanPeer(Torrent torrent, Peer peer);
    ConfigurationSection getConfig();
}
