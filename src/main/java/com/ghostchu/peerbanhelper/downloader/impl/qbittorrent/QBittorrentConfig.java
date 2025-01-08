package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent;

import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

public interface QBittorrentConfig {

    YamlConfiguration saveToYaml();

    String getType();

    String getEndpoint();

    String getUsername();

    String getPassword();

    QBittorrentBasicAuth getBasicAuth();

    String getHttpVersion();

    boolean isIncrementBan();

    boolean isUseShadowBan();

    boolean isVerifySsl();

    /**
 * Checks whether private torrents should be ignored during processing.
 *
 * @return {@code true} if private torrents are to be ignored, {@code false} otherwise
 */
boolean isIgnorePrivate();

    /**
 * Checks if the qBittorrent downloader is currently paused.
 *
 * @return {@code true} if the downloader is paused, {@code false} otherwise
 */
boolean isPaused();

    /**
 * Sets the paused state of the qBittorrent downloader.
 *
 * @param paused A boolean flag indicating whether the downloader should be paused (true) or resumed (false)
 */
void setPaused(boolean paused);

    /**
 * Sets the type of the qBittorrent configuration.
 *
 * @param type A string representing the configuration type to be set
 */
void setType(String type);

    /**
 * Sets the endpoint URL for the qBittorrent service.
 *
 * @param endpoint The URL endpoint where the qBittorrent service is hosted
 */
void setEndpoint(String endpoint);

    void setUsername(String username);

    void setPassword(String password);

    void setBasicAuth(QBittorrentBasicAuth basicAuth);

    void setHttpVersion(String httpVersion);

    void setIncrementBan(boolean incrementBan);

    void setUseShadowBan(boolean useShadowBan);

    void setVerifySsl(boolean verifySsl);

    void setIgnorePrivate(boolean ignorePrivate);
}
