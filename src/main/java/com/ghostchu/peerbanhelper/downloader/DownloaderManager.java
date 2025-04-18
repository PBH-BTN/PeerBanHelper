package com.ghostchu.peerbanhelper.downloader;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.BiglyBT;
import com.ghostchu.peerbanhelper.downloader.impl.bitcomet.BitComet;
import com.ghostchu.peerbanhelper.downloader.impl.deluge.Deluge;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.QBittorrent;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.enhanced.QBittorrentEE;
import com.ghostchu.peerbanhelper.downloader.impl.transmission.Transmission;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.ReloadStatus;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.configuration.MemoryConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public class DownloaderManager extends CopyOnWriteArrayList<Downloader> implements AutoCloseable, Reloadable {
    @Autowired
    private AlertManager alertManager;

    public DownloaderManager() {
        Main.getReloadManager().register(this);
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        close();
        loadDownloaders();
        return new ReloadResult(ReloadStatus.SUCCESS, "SUCCESS", null);
    }

    public void loadDownloaders() {
        this.clear();
        ConfigurationSection clientSection = Main.getMainConfig().getConfigurationSection("client");
        if (clientSection == null) {
            return;
        }
        for (String client : clientSection.getKeys(false)) {
            ConfigurationSection downloaderSection = clientSection.getConfigurationSection(client);
            String endpoint = downloaderSection.getString("endpoint");
            Downloader downloader = createDownloader(client, downloaderSection);
            registerDownloader(downloader);
            log.info(tlUI(Lang.DISCOVER_NEW_CLIENT, downloader.getType(), client, endpoint));
        }
    }

    public Downloader createDownloader(String client, ConfigurationSection downloaderSection) {
        if (downloaderSection.getString("name") != null) {
            downloaderSection.set("name", downloaderSection.getString("name", "").replace(".", "-"));
        }
        Downloader downloader = null;
        switch (downloaderSection.getString("type").toLowerCase(Locale.ROOT)) {
            case "qbittorrent" -> downloader = QBittorrent.loadFromConfig(client, downloaderSection, alertManager);
            case "qbittorrentee" -> downloader = QBittorrentEE.loadFromConfig(client, downloaderSection, alertManager);
            case "transmission" ->
                    downloader = Transmission.loadFromConfig(client, Main.getPbhServerAddress(), downloaderSection, alertManager);
            case "biglybt" -> downloader = BiglyBT.loadFromConfig(client, downloaderSection, alertManager);
            case "deluge" -> downloader = Deluge.loadFromConfig(client, downloaderSection, alertManager);
            case "bitcomet" -> downloader = BitComet.loadFromConfig(client, downloaderSection, alertManager);
            //case "rtorrent" -> downloader = RTorrent.loadFromConfig(client, downloaderSection);
        }
        return downloader;

    }

    public Downloader createDownloader(String client, JsonObject downloaderSection) {
        if (downloaderSection.get("name") != null) {
            downloaderSection.addProperty("name", downloaderSection.get("name").getAsString().replace(".", "-"));
        }
        Downloader downloader = null;
        switch (downloaderSection.get("type").getAsString().toLowerCase(Locale.ROOT)) {
            case "qbittorrent" -> downloader = QBittorrent.loadFromConfig(client, downloaderSection, alertManager);
            case "qbittorrentee" -> downloader = QBittorrentEE.loadFromConfig(client, downloaderSection, alertManager);
            case "transmission" ->
                    downloader = Transmission.loadFromConfig(client, Main.getPbhServerAddress(), downloaderSection, alertManager);
            case "biglybt" -> downloader = BiglyBT.loadFromConfig(client, downloaderSection, alertManager);
            case "deluge" -> downloader = Deluge.loadFromConfig(client, downloaderSection, alertManager);
            case "bitcomet" -> downloader = BitComet.loadFromConfig(client, downloaderSection, alertManager);
            //case "rtorrent" -> downloader = RTorrent.loadFromConfig(client, downloaderSection);
        }
        return downloader;

    }

    public void saveDownloaders() throws IOException {
        ConfigurationSection clientSection = new MemoryConfiguration();
        for (Downloader downloader : this) {
            clientSection.set(downloader.getName(), downloader.saveDownloader());
        }
        Main.getMainConfig().set("client", clientSection);
        Main.getMainConfig().save(Main.getMainConfigFile());
    }

    public boolean registerDownloader(Downloader downloader) {
        if (this.stream().anyMatch(d -> d.getName().equals(downloader.getName()))) {
            return false;
        }
        this.add(downloader);
        return true;
    }

    public void unregisterDownloader(Downloader downloader) {
        this.remove(downloader);
    }

    public List<Downloader> getDownloaders() {
        return List.copyOf(this);
    }

    @Override
    public void close() {
        for (Downloader d : this) {
            try {
                d.close();
            } catch (Exception e) {
                log.error(tlUI(Lang.UNABLE_CLOSE_DOWNLOADER, d.getName()), e);
            }
        }
    }
}
