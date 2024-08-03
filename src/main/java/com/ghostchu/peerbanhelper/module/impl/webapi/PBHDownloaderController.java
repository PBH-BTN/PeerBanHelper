package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.ipdb.IPGeoData;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.PopulatedPeerDTO;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Slf4j
@Component
public class PBHDownloaderController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;

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
                .patch("/api/downloaders/{downloaderName}", ctx -> handleDownloaderPatch(ctx, ctx.pathParam("downloaderName")), Role.USER_WRITE)
                .post("/api/downloaders/test", this::handleDownloaderTest, Role.USER_WRITE)
                .delete("/api/downloaders/{downloaderName}", ctx -> handleDownloaderDelete(ctx, ctx.pathParam("downloaderName")), Role.USER_WRITE)
                .get("/api/downloaders/{downloaderName}/status", ctx -> handleDownloaderStatus(ctx, ctx.pathParam("downloaderName")), Role.USER_READ)
                .get("/api/downloaders/{downloaderName}/torrents", ctx -> handleDownloaderTorrents(ctx, ctx.pathParam("downloaderName")), Role.USER_READ)
                .get("/api/downloaders/{downloaderName}/torrent/{torrentId}/peers", ctx -> handlePeersInTorrentOnDownloader(ctx, ctx.pathParam("downloaderName"), ctx.pathParam("torrentId")), Role.USER_READ);
    }

    private void handleDownloaderPut(Context ctx) {
        JsonObject draftDownloader = JsonParser.parseString(ctx.body()).getAsJsonObject();
        String name = draftDownloader.get("name").getAsString();
        if(name.contains(".")){
            throw new IllegalArgumentException("Illegal character (.) in name: " + name);
        }
        JsonObject config = draftDownloader.get("config").getAsJsonObject();
        Downloader downloader = getServer().createDownloader(name, config);
        if (downloader == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(Map.of("message", tl(locale(ctx), Lang.DOWNLOADER_API_ADD_FAILURE)));
            return;
        }
        if (getServer().registerDownloader(downloader)) {
            ctx.status(HttpStatus.CREATED);
            ctx.json(Map.of("message", tl(locale(ctx), Lang.DOWNLOADER_API_CREATED), "code", HttpStatus.CREATED.getCode()));
        } else {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(Map.of("message", tl(locale(ctx), Lang.DOWNLOADER_API_CREATION_FAILED_ALREADY_EXISTS)));
        }
        try {
            getServer().saveDownloaders();
        } catch (IOException e) {
            log.error("Internal server error, unable to create downloader due an I/O exception", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(Map.of("message", tl(locale(ctx), Lang.DOWNLOADER_API_CREATION_FAILED_IO_EXCEPTION)));
        }
    }

    private void handleDownloaderPatch(Context ctx, String downloaderName) {
        JsonObject draftDownloader = JsonParser.parseString(ctx.body()).getAsJsonObject();
        String name = draftDownloader.get("name").getAsString();
        if(name.contains(".")){
            throw new IllegalArgumentException("Illegal character (.) in name: " + name);
        }
        JsonObject config = draftDownloader.get("config").getAsJsonObject();
        Downloader downloader = getServer().createDownloader(name, config);
        if (downloader == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(Map.of("message", tl(locale(ctx), Lang.DOWNLOADER_API_UPDATE_FAILURE)));
            return;
        }
        // 可能重命名了？
        getServer().getDownloaders().stream()
                .filter(d -> d.getName().equals(downloaderName))
                .forEach(d -> getServer().unregisterDownloader(d));
        if (getServer().registerDownloader(downloader)) {
            ctx.status(HttpStatus.OK);
            ctx.json(Map.of("message", tl(locale(ctx), Lang.DOWNLOADER_API_UPDATED), "code", HttpStatus.OK.getCode()));
        } else {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(Map.of("message", tl(locale(ctx), Lang.DOWNLOADER_API_UPDATE_FAILURE_ALREADY_EXISTS)));
        }
        try {
            getServer().saveDownloaders();
        } catch (IOException e) {
            log.error("Internal server error, unable to update downloader due an I/O exception", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(Map.of("message", tl(locale(ctx), Lang.DOWNLOADER_API_CREATION_FAILED_IO_EXCEPTION)));
        }
    }

    private void handleDownloaderTest(Context ctx) {
        JsonObject draftDownloader = JsonParser.parseString(ctx.body()).getAsJsonObject();
        String name = draftDownloader.get("name").getAsString();
        if(name.contains(".")){
            throw new IllegalArgumentException("Illegal character (.) in name: " + name);
        }
        JsonObject config = draftDownloader.get("config").getAsJsonObject();
//        if (getServer().getDownloaders().stream().anyMatch(d -> d.getName().equals(name))) {
//            ctx.status(HttpStatus.CONFLICT);
//            ctx.json(Map.of("message", Lang.DOWNLOADER_API_TEST_NAME_EXISTS));
//            return;
//        }
        Downloader downloader = getServer().createDownloader(name, config);
        if (downloader == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(Map.of("message", tl(locale(ctx), Lang.DOWNLOADER_API_ADD_FAILURE)));
            return;
        }
        try {
            var testResult = downloader.login();
            ctx.status(HttpStatus.OK);
            if (testResult.success()) {
                ctx.json(Map.of("message", tl(locale(ctx), Lang.DOWNLOADER_API_TEST_OK), "valid", testResult.success()));
            } else {
                ctx.json(Map.of("message", tl(locale(ctx), testResult.getMessage()), "valid", testResult.success()));
            }
            downloader.close();
        } catch (Exception e) {
            log.error("Validate downloader failed", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(Map.of("message", e.getMessage(), "valid", false));
        }
    }

    private void handleDownloaderDelete(Context ctx, String downloaderName) {
        Optional<Downloader> selected = getServer().getDownloaders().stream().filter(d -> d.getName().equals(downloaderName)).findFirst();
        if (selected.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(Map.of("message", tl(locale(ctx), Lang.DOWNLOADER_API_REMOVE_NOT_EXISTS)));
            return;
        }
        Downloader downloader = selected.get();
        getServer().unregisterDownloader(downloader);
        try {
            getServer().saveDownloaders();
            ctx.status(HttpStatus.OK);
            ctx.json(Map.of("message", tl(locale(ctx), Lang.DOWNLOADER_API_REMOVE_SAVED), "code", HttpStatus.OK.getCode()));
        } catch (IOException e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(Map.of("message", e.getClass().getName() + ": " + e.getMessage()));
        }

    }

    private void handlePeersInTorrentOnDownloader(Context ctx, String downloaderName, String torrentId) {
        Optional<Downloader> selected = getServer().getDownloaders().stream().filter(d -> d.getName().equals(downloaderName)).findFirst();
        if (selected.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(Map.of("message", tl(locale(ctx), Lang.DOWNLOADER_API_DOWNLOADER_NOT_EXISTS)));
            return;
        }
        Downloader downloader = selected.get();
        List<PopulatedPeerDTO> peerWrappers = getServer().getLivePeersSnapshot().values()
                .stream()
                .flatMap(Collection::stream)
                .filter(p -> p.getDownloader().equals(downloader.getName()))
                .filter(p -> p.getTorrent().getHash().equals(torrentId))
                .sorted((o1, o2) -> Long.compare(o2.getPeer().getUploadSpeed(), o1.getPeer().getUploadSpeed()))
                .map(this::populatePeerDTO)
                .toList();
        ctx.status(HttpStatus.OK);
        ctx.json(peerWrappers);
    }

    private PopulatedPeerDTO populatePeerDTO(PeerMetadata p) {
        PopulatedPeerDTO dto = new PopulatedPeerDTO(p.getPeer(), null);
        PeerBanHelperServer.IPDBResponse response = getServer().queryIPDB(p.getPeer().toPeerAddress());
        IPGeoData geoData = response.geoData().get();
        if (geoData != null) {
            dto.setGeo(geoData);
        }
        return dto;
    }

    private void handleDownloaderTorrents(@NotNull Context ctx, String downloaderName) {
        Optional<Downloader> selected = getServer().getDownloaders().stream()
                .filter(d -> d.getName().equals(downloaderName))
                .findFirst();
        if (selected.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(Map.of("message", tl(locale(ctx), Lang.DOWNLOADER_API_DOWNLOADER_NOT_EXISTS)));
            return;
        }
        Downloader downloader = selected.get();
        List<TorrentWrapper> torrentWrappers = getServer().getLivePeersSnapshot()
                .values().stream()
                .flatMap(Collection::stream)
                .filter(p -> p.getDownloader().equals(downloader.getName()))
                .map(PeerMetadata::getTorrent)
                .distinct()
                .sorted((o1, o2) -> Long.compare(o2.getRtUploadSpeed(), o1.getRtUploadSpeed()))
                .toList();
        ctx.status(HttpStatus.OK);
        ctx.json(torrentWrappers);
    }

    private void handleDownloaderStatus(@NotNull Context ctx, String downloaderName) {
        String locale = locale(ctx);
        Optional<Downloader> selected = getServer().getDownloaders().stream()
                .filter(d -> d.getName().equals(downloaderName))
                .findFirst();
        if (selected.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(Map.of("message", tl(locale, Lang.DOWNLOADER_API_DOWNLOADER_NOT_EXISTS)));
            return;
        }
        Downloader downloader = selected.get();
        DownloaderLastStatus lastStatus = downloader.getLastStatus();
        long activeTorrents = getServer().getLivePeersSnapshot().values()
                .stream()
                .flatMap(Collection::stream)
                .filter(p -> p.getDownloader().equals(downloader.getName()))
                .map(p -> p.getTorrent().getHash())
                .distinct().count();
        long activePeers = getServer().getLivePeersSnapshot().values()
                .stream()
                .flatMap(Collection::stream)
                .filter(p -> p.getDownloader().equals(downloader.getName()))
                .count();

        JsonObject config = downloader.saveDownloaderJson();
        ctx.status(HttpStatus.OK);
        ctx.json(new DownloaderStatus(lastStatus, tl(locale, downloader.getLastStatusMessage() == null ? new TranslationComponent(Lang.STATUS_TEXT_UNKNOWN) : downloader.getLastStatusMessage()), activeTorrents, activePeers, config));
    }

    private void handleDownloaderList(@NotNull Context ctx) {
        List<DownloaderWrapper> downloaders = getServer().getDownloaders().stream().map(d -> new DownloaderWrapper(d.getName(), d.getEndpoint(), d.getType().toLowerCase())).toList();
        ctx.status(HttpStatus.OK);
        ctx.json(downloaders);
    }


    @Override
    public void onDisable() {

    }

    record DraftDownloader(String name, JsonObject config) {
    }

    record DownloaderStatus(DownloaderLastStatus lastStatus, String lastStatusMessage,
                            long activeTorrents,
                            long activePeers, JsonObject config) {

    }

    record DownloaderWrapper(String name, String endpoint, String type) {
    }
}
