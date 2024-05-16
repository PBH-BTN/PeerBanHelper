package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.wrapper.PeerMetadata;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PBHDownloaderController extends AbstractFeatureModule {
    public PBHDownloaderController(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Downloader API";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-downloader";
    }

    @Override
    public void onEnable() {
        getServer().getJavalinWebContainer().getJavalin()
                .get("/api/downloaders", this::handleDownloaderList)
                .get("/api/downloader/{downloaderName}/status", ctx -> handleDownloaderStatus(ctx, ctx.pathParam("downloaderName")))
                .get("/api/downloader/{downloaderName}/torrents", ctx -> handleDownloaderTorrents(ctx, ctx.pathParam("downloaderName")))
                .get("/api/downloader/{downloaderName}/torrent/{torrentId}/peers", ctx -> handlePeersInTorrentOnDownloader(ctx, ctx.pathParam("downloaderName"), ctx.pathParam("torrentId")));

    }

    private void handlePeersInTorrentOnDownloader(Context ctx, String downloaderName, String torrentId) {
        Optional<Downloader> selected = getServer().getDownloaders().stream().filter(d -> d.getName().equals(downloaderName)).findFirst();
        if (selected.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(Map.of("message", "The requested downloader not registered in PeerBanHelper configuration"));
            return;
        }
        Downloader downloader = selected.get();
        List<PeerMetadata> peerWrappers = getServer().getLivePeersSnapshot().values().stream().filter(p -> p.getDownloader().equals(downloader.getName())).toList();
        ctx.status(HttpStatus.OK);
        ctx.json(peerWrappers);
    }

    private void handleDownloaderTorrents(@NotNull Context ctx, String downloaderName) {
        Optional<Downloader> selected = getServer().getDownloaders().stream().filter(d -> d.getName().equals(downloaderName)).findFirst();
        if (selected.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(Map.of("message", "The requested downloader not registered in PeerBanHelper configuration"));
            return;
        }
        Downloader downloader = selected.get();
        List<TorrentWrapper> torrentWrappers = getServer().getLivePeersSnapshot().values().stream().filter(p -> p.getDownloader().equals(downloader.getName()))
                .map(PeerMetadata::getTorrent)
                .distinct()
                .toList();
        ctx.status(HttpStatus.OK);
        ctx.json(torrentWrappers);
    }

    private void handleDownloaderStatus(@NotNull Context ctx, String downloaderName) {
        Optional<Downloader> selected = getServer().getDownloaders().stream().filter(d -> d.getName().equals(downloaderName)).findFirst();
        if (selected.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(Map.of("message", "The requested downloader not registered in PeerBanHelper configuration"));
            return;
        }
        Downloader downloader = selected.get();
        DownloaderLastStatus lastStatus = downloader.getLastStatus();
        long activeTorrents = getServer().getLivePeersSnapshot().values()
                .stream()
                .filter(p -> p.getDownloader().equals(downloader.getName()))
                .map(p -> p.getTorrent().getHash())
                .distinct().count();
        long activePeers = getServer().getLivePeersSnapshot().values()
                .stream()
                .filter(p -> p.getDownloader().equals(downloader.getName()))
                .count();
        ctx.status(HttpStatus.OK);
        ctx.json(new DownloaderStatus(lastStatus, activeTorrents, activePeers));
    }

    private void handleDownloaderList(@NotNull Context ctx) {
        List<DownloaderWrapper> downloaders = getServer().getDownloaders().stream().map(d -> new DownloaderWrapper(d.getName(), d.getEndpoint(), d.getType())).toList();
        ctx.status(HttpStatus.OK);
        ctx.json(downloaders);
    }

    @Override
    public void onDisable() {

    }

    record DownloaderStatus(DownloaderLastStatus lastStatus, long activeTorrents, long activePeers) {

    }

    record DownloaderWrapper(String name, String endpoint, String type) {
    }
}
