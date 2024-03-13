package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

import java.util.concurrent.ExecutorService;

public interface FeatureModule {
    String getName();
    String getConfigName();
    boolean isModuleEnabled();
    BanResult shouldBanPeer(Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor);
    ConfigurationSection getConfig();
}
