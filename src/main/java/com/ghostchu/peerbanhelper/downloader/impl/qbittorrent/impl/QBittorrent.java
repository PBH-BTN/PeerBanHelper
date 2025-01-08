package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl;

import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.AbstractQbittorrent;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.QBittorrentConfig;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

@Slf4j
public class QBittorrent extends AbstractQbittorrent {

    /**
     * Constructs a new QBittorrent downloader instance.
     *
     * @param name The unique name identifying this QBittorrent downloader instance
     * @param config The configuration settings for the QBittorrent downloader
     * @param alertManager The alert management system for handling notifications and events
     */
    public QBittorrent(String name, QBittorrentConfig config, AlertManager alertManager) {
        super(name, config, alertManager);
    }

    /**
     * Checks if the qBittorrent downloader is currently paused.
     *
     * @return {@code true} if the downloader is paused, {@code false} otherwise
     */
    @Override
    public boolean isPaused() {
        return config.isPaused();
    }

    /**
     * Sets the paused state of the qBittorrent downloader.
     *
     * This method updates the paused state in both the parent class and the configuration object.
     * When called, it first invokes the superclass's setPaused method and then updates
     * the configuration's paused state accordingly.
     *
     * @param paused A boolean indicating whether the downloader should be paused (true) or resumed (false)
     */
    @Override
    public void setPaused(boolean paused) {
        super.setPaused(paused);
        config.setPaused(paused);
    }

    /**
     * Creates a QBittorrent instance from a JSON configuration object.
     *
     * @param name The name of the QBittorrent downloader instance
     * @param section The JSON configuration section containing QBittorrent settings
     * @param alertManager The alert manager for handling system alerts
     * @return A configured QBittorrent downloader instance
     * @throws JsonSyntaxException If the JSON configuration is malformed
     */
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
