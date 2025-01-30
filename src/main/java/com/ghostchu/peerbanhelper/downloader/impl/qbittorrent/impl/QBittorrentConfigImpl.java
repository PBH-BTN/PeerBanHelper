package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl;

import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.QBittorrentBasicAuth;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.QBittorrentConfig;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.util.Objects;

@NoArgsConstructor
@Data
public final class QBittorrentConfigImpl implements QBittorrentConfig {
    private String type;
    private String endpoint;
    private String username;
    private String password;
    private QBittorrentBasicAuth basicAuth;
    private String httpVersion;
    private boolean incrementBan;
    private boolean useShadowBan;
    private boolean verifySsl;
    private boolean ignorePrivate;
    private boolean paused;

    public static QBittorrentConfigImpl readFromYaml(ConfigurationSection section) {
        QBittorrentConfigImpl config = new QBittorrentConfigImpl();
        config.setType("qbittorrent");
        config.setEndpoint(section.getString("endpoint"));
        if (config.getEndpoint().endsWith("/")) { // 浏览器复制党 workaround 一下， 避免连不上的情况
            config.setEndpoint(config.getEndpoint().substring(0, config.getEndpoint().length() - 1));
        }
        config.setUsername(section.getString("username", ""));
        config.setPassword(section.getString("password", ""));
        QBittorrentBasicAuth basicauthDTO = new QBittorrentBasicAuth();
        basicauthDTO.setUser(section.getString("basic-auth.user"));
        basicauthDTO.setPass(section.getString("basic-auth.pass"));
        config.setBasicAuth(basicauthDTO);
        config.setHttpVersion(section.getString("http-version", "HTTP_1_1"));
        config.setIncrementBan(section.getBoolean("increment-ban", false));
        config.setUseShadowBan(section.getBoolean("use-shadow-ban", false));
        config.setVerifySsl(section.getBoolean("verify-ssl", true));
        config.setIgnorePrivate(section.getBoolean("ignore-private", false));
        config.setPaused(section.getBoolean("paused", false));
        return config;
    }

    @Override
    public YamlConfiguration saveToYaml() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("type", "qbittorrent");
        section.set("endpoint", endpoint);
        section.set("username", username);
        section.set("password", password);
        section.set("basic-auth.user", Objects.requireNonNullElse(basicAuth.getUser(), ""));
        section.set("basic-auth.pass", Objects.requireNonNullElse(basicAuth.getPass(), ""));
        section.set("http-version", httpVersion);
        section.set("increment-ban", incrementBan);
        section.set("use-shadow-ban", useShadowBan);
        section.set("verify-ssl", verifySsl);
        section.set("ignore-private", ignorePrivate);
        section.set("paused", paused);
        return section;
    }
}
