package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.enhanced;

import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.QBittorrentBasicAuth;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.QBittorrentConfig;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.util.Objects;

@NoArgsConstructor
@Data
public class QBittorrentEEConfigImpl implements QBittorrentConfig {
    private String type;
    private String endpoint;
    private String username;
    private String password;
    private QBittorrentBasicAuth basicAuth;
    private String httpVersion;
    private boolean incrementBan;
    private boolean verifySsl;
    private boolean useShadowBan;
    private boolean ignorePrivate;
    private boolean paused;

    /**
     * Creates a QBittorrentEEConfigImpl configuration object from a YAML configuration section.
     *
     * @param section The configuration section containing qBittorrent enhanced edition settings
     * @return A fully configured QBittorrentEEConfigImpl instance
     *
     * @implNote This method handles configuration parsing with default values and endpoint normalization.
     * It removes trailing slashes from the endpoint to prevent connection issues.
     *
     * @see QBittorrentBasicAuth
     */
    public static QBittorrentEEConfigImpl readFromYaml(ConfigurationSection section) {
        QBittorrentEEConfigImpl config = new QBittorrentEEConfigImpl();
        config.setType("qbittorrentee");
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

    /**
     * Saves the current qBittorrent Enhanced Edition configuration to a YAML configuration section.
     *
     * @return A YamlConfiguration object populated with the current configuration settings
     *
     * This method creates a new YAML configuration and populates it with:
     * - Configuration type (always "qbittorrentee")
     * - Endpoint URL
     * - Username and password
     * - Basic authentication credentials
     * - HTTP version
     * - Various boolean configuration flags including increment ban, shadow ban, SSL verification,
     *   private network handling, and paused state
     *
     * Null values for basic authentication user and password are replaced with empty strings
     * to ensure configuration integrity.
     */
    @Override
    public YamlConfiguration saveToYaml() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("type", "qbittorrentee");
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
