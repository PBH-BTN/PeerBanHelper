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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public final class DownloaderManager extends CopyOnWriteArrayList<Downloader> implements AutoCloseable, Reloadable {
    @Autowired
    private AlertManager alertManager;

    public DownloaderManager() {
        Main.getReloadManager().register(this);
        load();
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        load();
        return new ReloadResult(ReloadStatus.SUCCESS, "SUCCESS", null);
    }

    private void load() {
        close();
        loadDownloaders();
    }

    public void loadDownloaders() {
        this.clear();
        ConfigurationSection clientSection = Main.getMainConfig().getConfigurationSection("client");
        if (clientSection == null) {
            return;
        }
        for (String uuid : clientSection.getKeys(false)) {
            ConfigurationSection downloaderSection = clientSection.getConfigurationSection(uuid);
            String endpoint = downloaderSection.getString("endpoint");
            Downloader downloader = createDownloader(uuid, downloaderSection);
            registerDownloader(downloader);
            log.info(tlUI(Lang.DISCOVER_NEW_CLIENT, downloader.getType(), downloader.getName() + "(" + uuid + ")", endpoint));
        }
    }

    public Downloader createDownloader(String id, ConfigurationSection downloaderSection) {
        Downloader downloader = null;
        switch (downloaderSection.getString("type").toLowerCase(Locale.ROOT)) {
            case "qbittorrent" -> downloader = QBittorrent.loadFromConfig(id, downloaderSection, alertManager);
            case "qbittorrentee" -> downloader = QBittorrentEE.loadFromConfig(id, downloaderSection, alertManager);
            case "transmission" ->
                    downloader = Transmission.loadFromConfig(id, Main.getPbhServerAddress(), downloaderSection, alertManager);
            case "biglybt" -> downloader = BiglyBT.loadFromConfig(id, downloaderSection, alertManager);
            case "deluge" -> downloader = Deluge.loadFromConfig(id, downloaderSection, alertManager);
            case "bitcomet" -> downloader = BitComet.loadFromConfig(id, downloaderSection, alertManager);
            //case "rtorrent" -> downloader = RTorrent.loadFromConfig(client, downloaderSection);
        }
        return downloader;

    }

    public Downloader createDownloader(String id, JsonObject downloaderSection) {
        Downloader downloader = null;
        switch (downloaderSection.get("type").getAsString().toLowerCase(Locale.ROOT)) {
            case "qbittorrent" -> downloader = QBittorrent.loadFromConfig(id, downloaderSection, alertManager);
            case "qbittorrentee" -> downloader = QBittorrentEE.loadFromConfig(id, downloaderSection, alertManager);
            case "transmission" ->
                    downloader = Transmission.loadFromConfig(id, Main.getPbhServerAddress(), downloaderSection, alertManager);
            case "biglybt" -> downloader = BiglyBT.loadFromConfig(id, downloaderSection, alertManager);
            case "deluge" -> downloader = Deluge.loadFromConfig(id, downloaderSection, alertManager);
            case "bitcomet" -> downloader = BitComet.loadFromConfig(id, downloaderSection, alertManager);
            //case "rtorrent" -> downloader = RTorrent.loadFromConfig(client, downloaderSection);
        }
        return downloader;

    }

    public void saveDownloaders() throws IOException {
        ConfigurationSection clientSection = new MemoryConfiguration();
        for (Downloader downloader : this) {
            clientSection.set(downloader.getId(), downloader.saveDownloader());
        }
        Main.getMainConfig().set("client", clientSection);
        Main.getMainConfig().save(Main.getMainConfigFile());
    }

    public boolean registerDownloader(Downloader downloader) {
        if (this.stream().anyMatch(d -> d.getId().equals(downloader.getId()))) {
            return false;
        }
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

    @Nullable
    public Downloader getDownloaderById(String id) {
        for (Downloader downloader : this) {
            if (downloader.getId().equals(id)) {
                return downloader;
            }
        }
        return null;
    }

    @NotNull
    public DownloaderBasicInfo getDownloadInfo(@NotNull Downloader downloader) {
        return new DownloaderBasicInfo(downloader.getId(), downloader.getName(), downloader.getType());
    }

    @NotNull
    public DownloaderBasicInfo getDownloadInfo(@Nullable String id) {
        Downloader downloader = getDownloaderById(id);
        if (downloader != null && id != null) {
            return new DownloaderBasicInfo(downloader.getId(), downloader.getName(), downloader.getType());
        } else {
            return new DownloaderBasicInfo(id, "Unknown", "Unknown");
        }
    }


    @NotNull
    public List<Downloader> getDownloaderByName(String name) {
        return this.stream().filter(d -> d.getName().equals(name)).toList();
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
