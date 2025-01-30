package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl;

import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.AbstractQbittorrent;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.QBittorrentConfig;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

@Slf4j
public final class QBittorrent extends AbstractQbittorrent {

    public QBittorrent(String name, QBittorrentConfig config, AlertManager alertManager) {
        super(name, config, alertManager);
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

    public static QBittorrent loadFromConfig(String name, JsonObject section, AlertManager alertManager) {
        QBittorrentConfigImpl config = JsonUtil.getGson().fromJson(section.toString(), QBittorrentConfigImpl.class);
        return new QBittorrent(name, config, alertManager);
    }

    public static QBittorrent loadFromConfig(String name, ConfigurationSection section, AlertManager alertManager) {
        QBittorrentConfigImpl config = QBittorrentConfigImpl.readFromYaml(section);
        return new QBittorrent(name, config, alertManager);
    }

    @Override
    public String getType() {
        return "qBittorrent";
    }

}
