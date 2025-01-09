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

    boolean isIgnorePrivate();

    boolean isPaused();

    void setPaused(boolean paused);

    void setType(String type);

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
