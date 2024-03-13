package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.QBittorrent;
import com.ghostchu.peerbanhelper.downloader.impl.transmission.Transmission;
import com.ghostchu.peerbanhelper.text.Lang;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Main {

    public static void main(String[] args) throws InterruptedException {
        BuildMeta meta = new BuildMeta();
        try (InputStream stream = Main.class.getResourceAsStream("/build-info.yml")) {
            if (stream == null) {
                log.error(Lang.ERR_BUILD_NO_INFO_FILE);
            } else {
                String str = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                YamlConfiguration configuration = new YamlConfiguration();
                configuration.loadFromString(str);
                meta.loadBuildMeta(configuration);
            }
        } catch (IOException | InvalidConfigurationException e) {
            log.error(Lang.ERR_CANNOT_LOAD_BUILD_INFO, e);
        }


        log.info(Lang.MOTD, meta.getVersion());
        Unirest.config()
                .setDefaultHeader("User-Agent", "PeerBanHelper/" + meta.getVersion())
                .enableCookieManagement(true);
        List<Downloader> downloaderList = new ArrayList<>();
        log.info(Lang.LOADING_CONFIG);
        try {
            if (!initConfiguration()) {
                log.warn(Lang.CONFIG_PEERBANHELPER);
                return;
            }
        } catch (IOException e) {
            log.error(Lang.ERR_SETUP_CONFIGURATION, e);
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
                    log.info(Lang.DISCOVER_NEW_CLIENT, "qBittorrent", client, endpoint);
                }
                case "Transmission" -> {
                    downloaderList.add(new Transmission(client, endpoint, username, password, "http://" + mainConfig.getString("server.address") + ":" + mainConfig.getInt("server.http") + "/blocklist/transmission"));
                    log.info(Lang.DISCOVER_NEW_CLIENT, "Transmission",client, endpoint);
                }
            }
        }
        PeerBanHelperServer server = new PeerBanHelperServer(downloaderList,
                YamlConfiguration.loadConfiguration(new File("profile.yml")), mainConfig.getInt("server.http"));
        while (true) {
            Thread.sleep(30 * 1000);
        }
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