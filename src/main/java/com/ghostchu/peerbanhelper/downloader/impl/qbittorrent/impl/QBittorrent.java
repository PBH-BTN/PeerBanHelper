package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl;

import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.AbstractQbittorrent;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.QBittorrentConfig;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.traversal.NatAddressProvider;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

@Slf4j
public final class QBittorrent extends AbstractQbittorrent {

    public QBittorrent(String id, QBittorrentConfig config, AlertManager alertManager, HTTPUtil httpUtil, NatAddressProvider natAddressProvider) {
        super(id, config, alertManager, httpUtil, natAddressProvider);
    }

    @Override
    public boolean isPaused() {
        return config.isPaused();
    }

    @Override
    public void setPaused(boolean paused) {
        super.setPaused(paused);
        config.setPaused(paused);
    }

    public static QBittorrent loadFromConfig(String id, JsonObject section, AlertManager alertManager, HTTPUtil httpUtil, NatAddressProvider natAddressProvider) {
        QBittorrentConfigImpl config = JsonUtil.getGson().fromJson(section.toString(), QBittorrentConfigImpl.class);
        return new QBittorrent(id, config, alertManager, httpUtil, natAddressProvider);
    }

    public static QBittorrent loadFromConfig(String id, ConfigurationSection section, AlertManager alertManager, HTTPUtil httpUtil, NatAddressProvider natAddressProvider) {
        QBittorrentConfigImpl config = QBittorrentConfigImpl.readFromYaml(section, id);
        return new QBittorrent(id, config, alertManager, httpUtil, natAddressProvider);
    }

    @Override
    public @NotNull String getType() {
        return "qBittorrent";
    }

}
