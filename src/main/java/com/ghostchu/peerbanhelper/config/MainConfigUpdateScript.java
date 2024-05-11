package com.ghostchu.peerbanhelper.config;

import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

public class MainConfigUpdateScript {
    private final YamlConfiguration conf;

    public MainConfigUpdateScript(YamlConfiguration conf) {
        this.conf = conf;
    }

    @UpdateScript(version = 6)
    public void ip2location() {
        conf.set("ip2location.token", "");
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
