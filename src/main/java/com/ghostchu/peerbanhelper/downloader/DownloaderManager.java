package com.ghostchu.peerbanhelper.downloader;

import com.ghostchu.peerbanhelper.config.ConfigManager;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.QBittorrent;
import com.ghostchu.peerbanhelper.downloader.impl.transmission.Transmission;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Getter
@Slf4j
public class DownloaderManager {

    private final List<Downloader> downloaders = new ArrayList<>();

    public void loadDownloaders() {
        if (!downloaders.isEmpty())
            throw new RuntimeException("Downloaders has already loaded!");

        String serverAddress = ConfigManager.Sections.server().getPrefix();

        ConfigManager.Sections.client().getClients().forEach((name, item) -> {
            switch (item.type().toLowerCase(Locale.ROOT)) {
                case "qbittorrent" -> {
                    downloaders.add(new QBittorrent(
                            name,
                            item.endpoint(),
                            item.username(),
                            item.password(),
                            item.basicAuth().user(),
                            item.basicAuth().pass(),
                            item.verifySSL(),
                            item.httpVersion()
                    ));
                    log.info(Lang.DISCOVER_NEW_CLIENT, "qBittorrent", name, item.endpoint());
                }
                case "transmission" -> {
                    downloaders.add(new Transmission(
                            name,
                            item.endpoint(),
                            item.username(),
                            item.password(),
                            serverAddress + "/blocklist/transmission",
                            item.verifySSL(),
                            item.httpVersion()
                    ));
                    log.info(Lang.DISCOVER_NEW_CLIENT, "Transmission", name, item.endpoint());
                }
            }
        });
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

    public void reloadDownloaders() {
        stopAll();
        downloaders.clear();
        loadDownloaders();
    }

}
