package com.ghostchu.peerbanhelper.downloader;

import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.QBittorrent;
import com.ghostchu.peerbanhelper.downloader.impl.transmission.Transmission;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
public class DownloaderManager {

    @Getter
    private final List<Downloader> downloaders = new ArrayList<>();

    public void loadDownloaders(ConfigurationSection clientSection, String pbhServerAddress) {
        if (!downloaders.isEmpty())
            throw new RuntimeException("Downloaders has already loaded!");

        for (String client : clientSection.getKeys(false)) {
            ConfigurationSection downloaderSection = clientSection.getConfigurationSection(client);
            String endpoint = downloaderSection.getString("endpoint");
            String username = downloaderSection.getString("username");
            String password = downloaderSection.getString("password");
            String baUser = downloaderSection.getString("basic-auth.user");
            String baPass = downloaderSection.getString("basic-auth.pass");
            String httpVersion = downloaderSection.getString("http-version", "HTTP_1_1");
            HttpClient.Version httpVersionEnum;
            try {
                httpVersionEnum = HttpClient.Version.valueOf(httpVersion);
            } catch (IllegalArgumentException e) {
                httpVersionEnum = HttpClient.Version.HTTP_1_1;
            }
            boolean verifySSL = downloaderSection.getBoolean("verify-ssl", true);
            switch (downloaderSection.getString("type").toLowerCase(Locale.ROOT)) {
                case "qbittorrent" -> {
                    downloaders.add(new QBittorrent(client, endpoint, username, password, baUser, baPass, verifySSL, httpVersionEnum));
                    log.info(Lang.DISCOVER_NEW_CLIENT, "qBittorrent", client, endpoint);
                }
                case "transmission" -> {
                    downloaders.add(new Transmission(client, endpoint, username, password, pbhServerAddress + "/blocklist/transmission", verifySSL, httpVersionEnum));
                    log.info(Lang.DISCOVER_NEW_CLIENT, "Transmission", client, endpoint);
                }
            }
        }
    }

    public void stopAll(){
        this.downloaders.forEach(d-> {
            try {
                d.close();
            } catch (Exception e) {
                log.error("Failed to close download {}", d.getName(), e);
            }
        });
    }

}
