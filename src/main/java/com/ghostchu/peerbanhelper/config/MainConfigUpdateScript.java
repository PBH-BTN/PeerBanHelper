package com.ghostchu.peerbanhelper.config;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.File;
import java.util.UUID;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class MainConfigUpdateScript {
    private final YamlConfiguration conf;

    public MainConfigUpdateScript(YamlConfiguration conf) {
        this.conf = conf;
        validate();
    }

    private void validate() {
        String token = conf.getString("server.token");
        if (token == null || token.isBlank() || token.length() < 8) {
            conf.set("server.token", UUID.randomUUID().toString());
            log.info(tlUI(Lang.TOO_WEAK_TOKEN));
        }
    }

    @UpdateScript(version = 9)
    public void firewallIntegration() {
        conf.set("firewall-integration", null);
        conf.set("firewall-integration.windows-adv-firewall", true);
    }

    @UpdateScript(version = 8)
    public void webToken() {
        conf.set("server.token", UUID.randomUUID().toString());
    }

    @UpdateScript(version = 7)
    public void virtualThreads() {
        conf.set("threads", null);
    }

    @UpdateScript(version = 6)
    public void maxmindIpDatabase() {
        conf.set("ip-database.account-id", "");
        conf.set("ip-database.license-key", "");
        conf.set("ip-database.database-city", "GeoLite2-City");
        conf.set("ip-database.database-asn", "GeoLite2-ASN");
        conf.set("ip-database.auto-update", true);
        File file = new File(Main.getDataDirectory(), "banlist.dump");
        if (file.exists()) {
            file.delete(); // 因格式变动，需要删除旧版本的 banlist 持久化信息
        }
    }

    @UpdateScript(version = 5)
    public void optionForDnsReverseLookup() {
        conf.set("lookup.dns-reverse-lookup", false);
    }

    @UpdateScript(version = 4)
    public void defTurnOffIncrementBans() {
        ConfigurationSection section = conf.getConfigurationSection("client");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection downloader = section.getConfigurationSection(key);
            if (downloader != null) {
                if (downloader.getString("type", "").equalsIgnoreCase("qBittorrent")) {
                    downloader.set("increment-ban", false);
                }
            }
        }
    }


    @UpdateScript(version = 3)
    public void transmissionCustomRPCUrl() {
        ConfigurationSection section = conf.getConfigurationSection("client");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection downloader = section.getConfigurationSection(key);
            if (downloader != null) {
                if (downloader.getString("type", "").equalsIgnoreCase("Transmission")) {
                    downloader.set("rpc-url", "/transmission/rpc");
                }
            }
        }

    }

    @UpdateScript(version = 2)
    public void addPersistBanlist() {
        conf.set("persist.banlist", true);
    }

    @UpdateScript(version = 1)
    public void addIncrementBan() {
        ConfigurationSection section = conf.getConfigurationSection("client");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection downloader = section.getConfigurationSection(key);
            if (downloader != null) {
                if (downloader.getString("type", "").equalsIgnoreCase("qBittorrent")) {
                    downloader.set("increment-ban", true);
                }
            }
        }
    }
}
