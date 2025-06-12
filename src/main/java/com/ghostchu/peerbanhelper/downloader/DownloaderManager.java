package com.ghostchu.peerbanhelper.downloader;

import com.google.gson.JsonObject;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

public interface DownloaderManager extends List<Downloader>, AutoCloseable {
    void loadDownloaders();

    Downloader createDownloader(String id, ConfigurationSection downloaderSection);

    Downloader createDownloader(String id, JsonObject downloaderSection);

    void saveDownloaders() throws IOException;

    boolean registerDownloader(Downloader downloader);

    void unregisterDownloader(Downloader downloader);

    List<Downloader> getDownloaders();

    @Nullable Downloader getDownloaderById(String id);

    @NotNull DownloaderBasicInfo getDownloadInfo(@NotNull Downloader downloader);

    @NotNull DownloaderBasicInfo getDownloadInfo(@Nullable String id);

    @NotNull List<Downloader> getDownloaderByName(String name);
}
