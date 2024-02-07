package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.QBittorrent;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Main {
    public static void main(String[] args) {
        log.info("PeerBanHelper - v1.0 - by Ghost_chu");
        Unirest.config().setDefaultHeader("User-Agent", "PeerBanHelper/1.0");
        List<Downloader> downloaderList = new ArrayList<>();
        log.info("加载配置文件……");
        try {
            if (!initConfiguration()) {
                log.warn("请配置 config.yml 和 profile.yml 后，重新启动 PeerBanHelper。");
                return;
            }
        } catch (IOException e) {
            log.error("初始化配置文件时出错", e);
            return;
        }
        YamlConfiguration mainConfig = YamlConfiguration.loadConfiguration(new File("config.yml"));
        ConfigurationSection clientSection = mainConfig.getConfigurationSection("client");
        for (String client : clientSection.getKeys(false)) {
            ConfigurationSection downloaderSection = clientSection.getConfigurationSection(client);
            String endpoint = downloaderSection.getString("endpoint");
            String username = downloaderSection.getString("username");
            String password = downloaderSection.getString("password");
            switch (downloaderSection.getString("type").toLowerCase()) {
                case "qbittorrent" -> {
                    downloaderList.add(new QBittorrent(client, endpoint, username, password));
                    log.info(" + qBittorrent -> {} ({})", client, endpoint);
                }
            }
        }
        PeerBanHelperServer server = new PeerBanHelperServer(downloaderList, YamlConfiguration.loadConfiguration(new File("profile.yml")));

    }

    private static boolean initConfiguration() throws IOException {
        boolean exists = true;
        File config = new File("config.yml");
        File profile = new File("profile.yml");
        if (!config.exists()) {
            exists = false;
            Files.copy(Main.class.getResourceAsStream("/config.yml"), config.toPath());
        }
        if (!profile.exists()) {
            exists = false;
            Files.copy(Main.class.getResourceAsStream("/profile.yml"), profile.toPath());
        }
        return exists;
    }
}