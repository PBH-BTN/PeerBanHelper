package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl;

import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.AbstractQbittorrent;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.QBittorrentConfig;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

@Slf4j
public class QBittorrent extends AbstractQbittorrent {

    public QBittorrent(String name, QBittorrentConfig config) {
        super(name, config);
    }

    public static QBittorrent loadFromConfig(String name, JsonObject section) {
        QBittorrentConfigImpl config = JsonUtil.getGson().fromJson(section.toString(), QBittorrentConfigImpl.class);
        return new QBittorrent(name, config);
    }

    public static QBittorrent loadFromConfig(String name, ConfigurationSection section) {
        QBittorrentConfigImpl config = QBittorrentConfigImpl.readFromYaml(section);
        return new QBittorrent(name, config);
    }

    @Override
    public String getType() {
        return "qBittorrent";
    }

}
