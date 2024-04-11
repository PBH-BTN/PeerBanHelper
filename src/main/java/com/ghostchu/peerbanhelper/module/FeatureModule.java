package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

import java.util.concurrent.ExecutorService;

public interface FeatureModule {
    String getName();

    boolean isModuleEnabled();

    BanResult shouldBanPeer(Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor);

    void stop();

    void register();
}
