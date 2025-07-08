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
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.ReloadStatus;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.configuration.MemoryConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public final class DownloaderManagerImpl extends CopyOnWriteArrayList<Downloader> implements Reloadable, DownloaderManager {

    private final AlertManager alertManager;
    private final HTTPUtil httpUtil;

    public DownloaderManagerImpl(AlertManager alertManager, HTTPUtil httpUtil) {
        this.alertManager = alertManager;
        this.httpUtil = httpUtil;
        Main.getReloadManager().register(this);
        load();
    }

    @Override
    public ReloadResult reloadModule() {
        load();
        return new ReloadResult(ReloadStatus.SUCCESS, "SUCCESS", null);
    }

    private void load() {
        close();
        loadDownloaders();
    }

    @Override
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

    @Override
    public Downloader createDownloader(String id, ConfigurationSection downloaderSection) {
        var builder =  httpUtil.newBuilder();
        Downloader downloader = null;
        switch (downloaderSection.getString("type").toLowerCase(Locale.ROOT)) {
            case "qbittorrent" -> downloader = QBittorrent.loadFromConfig(id, downloaderSection, alertManager,builder);
            case "qbittorrentee" -> downloader = QBittorrentEE.loadFromConfig(id, downloaderSection, alertManager, builder);
            case "transmission" ->
                    downloader = Transmission.loadFromConfig(id, Main.getPbhServerAddress(), downloaderSection, alertManager,builder);
            case "biglybt" -> downloader = BiglyBT.loadFromConfig(id, downloaderSection, alertManager, builder);
            case "raccoonfink/deluge" -> downloader = Deluge.loadFromConfig(id, downloaderSection, alertManager, builder);
            case "bitcomet" -> downloader = BitComet.loadFromConfig(id, downloaderSection, alertManager, builder);
            //case "rtorrent" -> downloader = RTorrent.loadFromConfig(client, downloaderSection);
        }
        return downloader;

    }

    @Override
    public Downloader createDownloader(String id, JsonObject downloaderSection) {
        var builder =  httpUtil.newBuilder();
        Downloader downloader = null;
        switch (downloaderSection.get("type").getAsString().toLowerCase(Locale.ROOT)) {
            case "qbittorrent" -> downloader = QBittorrent.loadFromConfig(id, downloaderSection, alertManager, builder);
            case "qbittorrentee" -> downloader = QBittorrentEE.loadFromConfig(id, downloaderSection, alertManager, builder);
            case "transmission" ->
                    downloader = Transmission.loadFromConfig(id, Main.getPbhServerAddress(), downloaderSection, alertManager, builder);
            case "biglybt" -> downloader = BiglyBT.loadFromConfig(id, downloaderSection, alertManager, builder);
            case "raccoonfink/deluge" -> downloader = Deluge.loadFromConfig(id, downloaderSection, alertManager, builder);
            case "bitcomet" -> downloader = BitComet.loadFromConfig(id, downloaderSection, alertManager, builder);
            //case "rtorrent" -> downloader = RTorrent.loadFromConfig(client, downloaderSection);
        }
        return downloader;

    }

    @Override
    public void saveDownloaders() throws IOException {
        ConfigurationSection clientSection = new MemoryConfiguration();
        for (Downloader downloader : this) {
            clientSection.set(downloader.getId(), downloader.saveDownloader());
        }
        Main.getMainConfig().set("client", clientSection);
        Main.getMainConfig().save(Main.getMainConfigFile());
    }

    @Override
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

    @Override
    public void unregisterDownloader(Downloader downloader) {
        this.remove(downloader);
    }

    @Override
    public List<Downloader> getDownloaders() {
        return List.copyOf(this);
    }

    @Override
    public @Nullable Downloader getDownloaderById(String id) {
        for (Downloader downloader : this) {
            if (downloader.getId().equals(id)) {
                return downloader;
            }
        }
        return null;
    }

    @Override
    public @NotNull DownloaderBasicInfo getDownloadInfo(@NotNull Downloader downloader) {
        return new DownloaderBasicInfo(downloader.getId(), downloader.getName(), downloader.getType());
    }

    @Override
    public @NotNull DownloaderBasicInfo getDownloadInfo(@Nullable String id) {
        Downloader downloader = getDownloaderById(id);
        if (downloader != null && id != null) {
            return new DownloaderBasicInfo(downloader.getId(), downloader.getName(), downloader.getType());
        } else {
            return new DownloaderBasicInfo(id, "Unknown", "Unknown");
        }
    }


    @Override
    public @NotNull List<Downloader> getDownloaderByName(String name) {
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
