package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.DownloaderServer;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelper;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.downloader.DownloaderManager;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.PopulatedPeerDTO;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.util.dns.DNSLookup;
import com.ghostchu.peerbanhelper.util.ipdb.IPGeoData;
import com.ghostchu.peerbanhelper.util.lab.Experiments;
import com.ghostchu.peerbanhelper.util.lab.Laboratory;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.peerbanhelper.wrapper.PeerMetadata;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Slf4j
@Component
@IgnoreScan
public final class PBHDownloaderController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private Laboratory laboratory;
    @Autowired
    private DNSLookup dnsLookup;
    @Autowired
    private DownloaderManager downloaderManager;
    @Autowired
    private DownloaderServer downloaderServer;

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
        webContainer.javalin()
                .get("/api/downloaders", this::handleDownloaderList, Role.USER_READ)
                .put("/api/downloaders", this::handleDownloaderPut, Role.USER_WRITE)
                .patch("/api/downloaders/{downloaderId}", ctx -> handleDownloaderPatch(ctx, ctx.pathParam("downloaderId")), Role.USER_WRITE)
                .post("/api/downloaders/test", this::handleDownloaderTest, Role.USER_WRITE)
                .delete("/api/downloaders/{downloaderId}", ctx -> handleDownloaderDelete(ctx, ctx.pathParam("downloaderId")), Role.USER_WRITE)
                .get("/api/downloaders/{downloaderId}/status", ctx -> handleDownloaderStatus(ctx, ctx.pathParam("downloaderId")), Role.USER_READ)
                .get("/api/downloaders/{downloaderId}/torrents", ctx -> handleDownloaderTorrents(ctx, ctx.pathParam("downloaderId")), Role.USER_READ)
                .get("/api/downloaders/{downloaderId}/torrent/{torrentId}/peers", ctx -> handlePeersInTorrentOnDownloader(ctx, ctx.pathParam("downloaderId"), ctx.pathParam("torrentId")), Role.USER_READ);
    }

    private void handleDownloaderPut(Context ctx) {
        JsonObject draftDownloader = JsonParser.parseString(ctx.body()).getAsJsonObject();
        String id = draftDownloader.get("id").getAsString();
        JsonObject config = draftDownloader.get("config").getAsJsonObject();
        Downloader downloader = downloaderManager.createDownloader(id, config);
        if (downloader == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_ADD_FAILURE), null));
            return;
        }
        if (downloaderManager.registerDownloader(downloader)) {
            ctx.status(HttpStatus.CREATED);
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.DOWNLOADER_API_CREATED), null));
        } else {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_CREATION_FAILED_ALREADY_EXISTS), null));
            return;
        }
        try {
            downloaderManager.saveDownloaders();
        } catch (IOException e) {
            log.error("Internal server error, unable to create downloader due an I/O exception", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_CREATION_FAILED_IO_EXCEPTION), null));
        }
    }

    private void handleDownloaderPatch(Context ctx, String downloaderId) {
        JsonObject draftDownloader = JsonParser.parseString(ctx.body()).getAsJsonObject();
        JsonObject config = draftDownloader.get("config").getAsJsonObject();
        Downloader downloader = downloaderManager.createDownloader(downloaderId, config);
        if (downloader == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_UPDATE_FAILURE), null));
            return;
        }
        // 可能重命名了？
        downloaderManager.stream()
                .filter(d -> d.getId().equals(downloaderId))
                .forEach(d -> downloaderManager.unregisterDownloader(d));
        if (downloaderManager.registerDownloader(downloader)) {
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.DOWNLOADER_API_UPDATED), null));
        } else {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_UPDATE_FAILURE_ALREADY_EXISTS), null));
        }
        try {
            downloaderManager.saveDownloaders();
        } catch (IOException e) {
            log.error("Internal server error, unable to update downloader due an I/O exception", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_CREATION_FAILED_IO_EXCEPTION), null));
        }
    }

    private void handleDownloaderTest(Context ctx) {
        JsonObject draftDownloader = JsonParser.parseString(ctx.body()).getAsJsonObject();
        JsonObject config = draftDownloader.get("config").getAsJsonObject();
//        if (getServer().getDownloaders().stream().anyMatch(d -> d.getName().equals(name))) {
//            ctx.status(HttpStatus.CONFLICT);
//            ctx.json(Map.of("message", Lang.DOWNLOADER_API_TEST_NAME_EXISTS));
//            return;
//        }
        String id = draftDownloader.get("id").getAsString();
        Downloader downloader = downloaderManager.createDownloader(id, config);
        if (downloader == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_ADD_FAILURE), null));
            return;
        }
        try {
            if (!downloader.isPaused()) {
                var testResult = downloader.login();
                if (testResult.success()) {
                    ctx.json(new StdResp(true, tl(locale(ctx), Lang.DOWNLOADER_API_TEST_OK), null));
                } else {
                    ctx.json(new StdResp(false, tl(locale(ctx), testResult.getMessage()), null));
                }
                downloader.close();
            } else {
                ctx.json(new StdResp(true, tl(locale(ctx), Lang.DOWNLOADER_API_TEST_BYPASS_PAUSED), null));
            }
        } catch (Exception e) {
            log.error("Validate downloader failed", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(new StdResp(false, e.getMessage(), null));
        }
    }

    private void handleDownloaderDelete(Context ctx, String downloaderId) {
        Optional<Downloader> selected = downloaderManager.stream().filter(d -> d.getId().equals(downloaderId)).findFirst();
        if (selected.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_REMOVE_NOT_EXISTS), null));
            return;
        }
        Downloader downloader = selected.get();
        downloaderManager.unregisterDownloader(downloader);
        try {
            downloaderManager.saveDownloaders();
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.DOWNLOADER_API_REMOVE_SAVED), null));
        } catch (IOException e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(new StdResp(false, e.getClass().getName() + ": " + e.getMessage(), null));
        }

    }

    private void handlePeersInTorrentOnDownloader(Context ctx, String downloaderId, String torrentId) {
        Optional<Downloader> selected = downloaderManager.stream().filter(d -> d.getId().equals(downloaderId)).findFirst();
        if (selected.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_DOWNLOADER_NOT_EXISTS), null));
            return;
        }
        Downloader downloader = selected.get();
        boolean ptr = Main.getMainConfig().getBoolean("lookup.dns-reverse-lookup");
        List<PopulatedPeerDTO> peerWrappers = downloaderServer.getLivePeersSnapshot().values()
                .stream()
                .flatMap(Collection::parallelStream)
                .filter(p -> p.getUniqueId().equals(downloader.getId()))
                .filter(p -> p.getTorrent().getId().equals(torrentId))
                .sorted((o1, o2) -> Long.compare(o2.getPeer().getUploadSpeed(), o1.getPeer().getUploadSpeed()))
                .map(dat -> populatePeerDTO(dat, ptr))
                .toList();
        ctx.json(new StdResp(true, null, peerWrappers));
    }

    private PopulatedPeerDTO populatePeerDTO(PeerMetadata p, boolean resolvePTR) {
        PopulatedPeerDTO dto = new PopulatedPeerDTO(p.getPeer(), null, null);
        PeerBanHelper.IPDBResponse response = getServer().queryIPDB(p.getPeer().toPeerAddress());
        IPGeoData geoData = response.geoData().get();
        if (geoData != null) {
            dto.setGeo(geoData);
        }
        if (dto.getPtrRecord() == null && resolvePTR) {
            if (laboratory.isExperimentActivated(Experiments.DNSJAVA.getExperiment())) {
                dto.setPtrRecord(dnsLookup.ptr(IPAddressUtil.getIPAddress(p.getPeer().getAddress().getIp()).toReverseDNSLookupString()).join().orElse(null));
            } else {
                try {
                    dto.setPtrRecord(InetAddress.getByName(p.getPeer().getAddress().getIp()).getCanonicalHostName());
                } catch (UnknownHostException e) {
                    dto.setPtrRecord(null);
                }
            }
        }

        return dto;
    }

    private void handleDownloaderTorrents(@NotNull Context ctx, String downloaderId) {
        Optional<Downloader> selected = downloaderManager.stream()
                .filter(d -> d.getId().equals(downloaderId))
                .findFirst();
        if (selected.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_DOWNLOADER_NOT_EXISTS), null));
            return;
        }
        Downloader downloader = selected.get();
        List<TorrentWrapper> torrentWrappers = downloaderServer.getLivePeersSnapshot()
                .values().stream()
                .flatMap(Collection::stream)
                .filter(p -> p.getUniqueId().equals(downloader.getId()))
                .map(PeerMetadata::getTorrent)
                .distinct()
                .sorted((o1, o2) -> {
                    var compare = Long.compare(o2.getRtUploadSpeed(), o1.getRtUploadSpeed());
                    if (compare != 0) {
                        return compare;
                    } else {
                        return Long.compare(o2.getRtDownloadSpeed(), o1.getRtDownloadSpeed());
                    }
                })
                .toList();
        ctx.json(new StdResp(true, null, torrentWrappers));
    }

    private void handleDownloaderStatus(@NotNull Context ctx, String downloaderId) {
        String locale = locale(ctx);
        Optional<Downloader> selected = downloaderManager.stream()
                .filter(d -> d.getId().equals(downloaderId))
                .findFirst();
        if (selected.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_DOWNLOADER_NOT_EXISTS), null));
            return;
        }
        Downloader downloader = selected.get();
        DownloaderLastStatus lastStatus = downloader.getLastStatus();
        long activeTorrents = downloaderServer.getLivePeersSnapshot().values()
                .stream()
                .flatMap(Collection::stream)
                .filter(p -> p.getUniqueId().equals(downloader.getId()))
                .map(p -> p.getTorrent().getHash())
                .distinct().count();
        long activePeers = downloaderServer.getLivePeersSnapshot().values()
                .stream()
                .flatMap(Collection::stream)
                .filter(p -> p.getUniqueId().equals(downloader.getId()))
                .count();

        JsonObject config = downloader.saveDownloaderJson();
        ctx.json(new StdResp(true, null, new DownloaderStatus(lastStatus, tl(locale, downloader.getLastStatusMessage() == null ? new TranslationComponent(Lang.STATUS_TEXT_UNKNOWN) : downloader.getLastStatusMessage()), activeTorrents, activePeers, config, downloader.isPaused())));
    }

    private void handleDownloaderList(@NotNull Context ctx) {
        List<DownloaderWrapper> downloaders = downloaderManager
                .stream().map(d -> new DownloaderWrapper(d.getId(), d.getName(), d.getEndpoint(), d.getType().toLowerCase(), d.isPaused()))
                .toList();
        ctx.json(new StdResp(true, null, downloaders));
    }


    @Override
    public void onDisable() {

    }

    record DraftDownloader(String id, String name, JsonObject config) {
    }

    record DownloaderStatus(DownloaderLastStatus lastStatus, String lastStatusMessage,
                            long activeTorrents,
                            long activePeers, JsonObject config, boolean paused) {

    }

    record DownloaderWrapper(String id, String name, String endpoint, String type, boolean paused) {
    }
}
