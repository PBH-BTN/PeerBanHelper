package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.ipdb.IPGeoData;
import com.ghostchu.peerbanhelper.lab.Experiments;
import com.ghostchu.peerbanhelper.lab.Laboratory;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.PopulatedPeerDTO;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.util.dns.DNSLookup;
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
public class PBHDownloaderController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private Laboratory laboratory;
    @Autowired
    private DNSLookup dnsLookup;

    /**
     * Indicates whether the module is configurable.
     *
     * @return Always returns {@code false}, signifying that this module cannot be dynamically configured at runtime.
     */
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

    /**
     * Handles the creation of a new downloader via HTTP PUT request.
     *
     * @param ctx The Javalin context containing the request details
     * @throws IllegalArgumentException If the downloader name contains illegal characters
     *
     * This method processes a JSON payload to create a new downloader with optional paused state.
     * It performs the following key operations:
     * - Validates the downloader name (no periods allowed)
     * - Extracts configuration and optional paused state from the request
     * - Attempts to create and register the downloader
     * - Saves the downloader configuration
     *
     * Possible HTTP responses:
     * - 201 CREATED: Downloader successfully created
     * - 400 BAD_REQUEST: Downloader creation failed (invalid config or already exists)
     * - 500 INTERNAL_SERVER_ERROR: Unable to save downloader configuration
     */
    private void handleDownloaderPut(Context ctx) {
        JsonObject draftDownloader = JsonParser.parseString(ctx.body()).getAsJsonObject();
        String name = draftDownloader.get("name").getAsString();
        if (name.contains(".")) {
            throw new IllegalArgumentException("Illegal character (.) in name: " + name);
        }
        boolean paused = draftDownloader.has("paused") && draftDownloader.get("paused").getAsBoolean();
        JsonObject config = draftDownloader.get("config").getAsJsonObject();
        Downloader downloader = getServer().createDownloader(name, config);
        if (downloader == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_ADD_FAILURE), null));
            return;
        }
        downloader.setPaused(paused);
        if (getServer().registerDownloader(downloader)) {
            ctx.status(HttpStatus.CREATED);
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.DOWNLOADER_API_CREATED), null));
        } else {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_CREATION_FAILED_ALREADY_EXISTS), null));
        }
        try {
            getServer().saveDownloaders();
        } catch (IOException e) {
            log.error("Internal server error, unable to create downloader due an I/O exception", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_CREATION_FAILED_IO_EXCEPTION), null));
        }
    }

    /**
     * Handles the HTTP PATCH request to update an existing downloader configuration.
     *
     * @param ctx The Javalin context containing the request details
     * @param downloaderName The name of the downloader to be updated
     * @throws IllegalArgumentException If the new downloader name contains an illegal character (.)
     *
     * This method performs the following operations:
     * 1. Parses the request body JSON to extract downloader configuration
     * 2. Validates the new downloader name
     * 3. Creates a new downloader with the updated configuration
     * 4. Unregisters the old downloader
     * 5. Registers the new downloader
     * 6. Saves the updated downloader configuration
     *
     * Possible HTTP responses:
     * - 200 OK: Successful downloader update
     * - 400 Bad Request: Downloader creation failure or already exists
     * - 500 Internal Server Error: I/O exception during saving
     */
    private void handleDownloaderPatch(Context ctx, String downloaderName) {
        JsonObject draftDownloader = JsonParser.parseString(ctx.body()).getAsJsonObject();
        String name = draftDownloader.get("name").getAsString();
        if (name.contains(".")) {
            throw new IllegalArgumentException("Illegal character (.) in name: " + name);
        }
        boolean paused = draftDownloader.has("paused") && draftDownloader.get("paused").getAsBoolean();
        JsonObject config = draftDownloader.get("config").getAsJsonObject();
        Downloader downloader = getServer().createDownloader(name, config);
        if (downloader == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_UPDATE_FAILURE), null));
            return;
        }
        downloader.setPaused(paused);
        // 可能重命名了？
        getServer().getDownloaders().stream()
                .filter(d -> d.getName().equals(downloaderName))
                .forEach(d -> getServer().unregisterDownloader(d));
        if (getServer().registerDownloader(downloader)) {
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.DOWNLOADER_API_UPDATED), null));
        } else {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_UPDATE_FAILURE_ALREADY_EXISTS), null));
        }
        try {
            getServer().saveDownloaders();
        } catch (IOException e) {
            log.error("Internal server error, unable to update downloader due an I/O exception", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_CREATION_FAILED_IO_EXCEPTION), null));
        }
    }

    /**
     * Handles the testing of a downloader configuration via HTTP request.
     *
     * This method validates and tests a downloader configuration by:
     * - Parsing the incoming JSON request
     * - Validating the downloader name
     * - Creating a downloader instance
     * - Optionally testing the connection based on the paused state
     *
     * @param ctx The Javalin context containing the HTTP request details
     * @throws IllegalArgumentException If the downloader name contains illegal characters
     */
    private void handleDownloaderTest(Context ctx) {
        JsonObject draftDownloader = JsonParser.parseString(ctx.body()).getAsJsonObject();
        String name = draftDownloader.get("name").getAsString();
        if (name.contains(".")) {
            throw new IllegalArgumentException("Illegal character (.) in name: " + name);
        }
        boolean paused = draftDownloader.has("paused") && draftDownloader.get("paused").getAsBoolean();
        JsonObject config = draftDownloader.get("config").getAsJsonObject();
//        if (getServer().getDownloaders().stream().anyMatch(d -> d.getName().equals(name))) {
//            ctx.status(HttpStatus.CONFLICT);
//            ctx.json(Map.of("message", Lang.DOWNLOADER_API_TEST_NAME_EXISTS));
//            return;
//        }
        Downloader downloader = getServer().createDownloader(name, config);
        if (downloader == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_ADD_FAILURE), null));
            return;
        }
        downloader.setPaused(paused);
        try {
            if (!paused) {
                var testResult = downloader.login();
                if (testResult.success()) {
                    ctx.json(new StdResp(testResult.success(), tl(locale(ctx), Lang.DOWNLOADER_API_TEST_OK), null));
                } else {
                    ctx.json(new StdResp(testResult.success(), tl(locale(ctx), testResult.getMessage()), null));
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

    private void handleDownloaderDelete(Context ctx, String downloaderName) {
        Optional<Downloader> selected = getServer().getDownloaders().stream().filter(d -> d.getName().equals(downloaderName)).findFirst();
        if (selected.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_REMOVE_NOT_EXISTS), null));
            return;
        }
        Downloader downloader = selected.get();
        getServer().unregisterDownloader(downloader);
        try {
            getServer().saveDownloaders();
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.DOWNLOADER_API_REMOVE_SAVED), null));
        } catch (IOException e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(new StdResp(false, e.getClass().getName() + ": " + e.getMessage(), null));
        }

    }

    /**
     * Retrieves and returns a list of peers for a specific torrent on a given downloader.
     *
     * This method performs the following operations:
     * 1. Finds the specified downloader by name
     * 2. Retrieves live peer snapshots filtered by downloader and torrent
     * 3. Sorts peers by upload speed in descending order
     * 4. Populates peer data transfer objects with optional DNS reverse lookup
     *
     * @param ctx The Javalin context containing the HTTP request and response
     * @param downloaderName The name of the downloader to retrieve peers from
     * @param torrentId The unique identifier of the torrent to filter peers
     *
     * @throws HttpResponseException If the specified downloader is not found
     *
     * @return A standard response containing a list of populated peer DTOs or an error message
     */
    private void handlePeersInTorrentOnDownloader(Context ctx, String downloaderName, String torrentId) {
        Optional<Downloader> selected = getServer().getDownloaders().stream().filter(d -> d.getName().equals(downloaderName)).findFirst();
        if (selected.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_DOWNLOADER_NOT_EXISTS), null));
            return;
        }
        Downloader downloader = selected.get();
        boolean ptr = Main.getMainConfig().getBoolean("lookup.dns-reverse-lookup");
        List<PopulatedPeerDTO> peerWrappers = getServer().getLivePeersSnapshot().values()
                .stream()
                .flatMap(Collection::parallelStream)
                .filter(p -> p.getDownloader().equals(downloader.getName()))
                .filter(p -> p.getTorrent().getId().equals(torrentId))
                .sorted((o1, o2) -> Long.compare(o2.getPeer().getUploadSpeed(), o1.getPeer().getUploadSpeed()))
                .map(dat -> populatePeerDTO(dat, ptr))
                .toList();
        ctx.json(new StdResp(true, null, peerWrappers));
    }

    /**
     * Populates a Peer Data Transfer Object (DTO) with detailed information about a peer.
     *
     * This method enriches a basic peer metadata object with geographical information
     * and optionally resolves the PTR (Pointer) record for the peer's IP address.
     *
     * @param p The peer metadata containing basic peer information
     * @param resolvePTR A flag indicating whether to attempt PTR record resolution
     * @return A populated PeerDTO with geographical and optional PTR record information
     */
    private PopulatedPeerDTO populatePeerDTO(PeerMetadata p, boolean resolvePTR) {
        PopulatedPeerDTO dto = new PopulatedPeerDTO(p.getPeer(), null, null);
        PeerBanHelperServer.IPDBResponse response = getServer().queryIPDB(p.getPeer().toPeerAddress());
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

    /**
     * Retrieves and returns a sorted list of torrents for a specific downloader.
     *
     * This method filters live peers by the specified downloader name, extracts unique torrents,
     * and sorts them primarily by real-time upload speed and secondarily by real-time download speed.
     *
     * @param ctx The Javalin context containing the HTTP request and response
     * @param downloaderName The name of the downloader to retrieve torrents for
     *
     * @throws NullPointerException if the context or downloader name is null
     *
     * @apiNote Returns HTTP 404 if the specified downloader does not exist
     * @apiNote Returns a JSON response with a list of sorted {@link TorrentWrapper} objects
     */
    private void handleDownloaderTorrents(@NotNull Context ctx, String downloaderName) {
        Optional<Downloader> selected = getServer().getDownloaders().stream()
                .filter(d -> d.getName().equals(downloaderName))
                .findFirst();
        if (selected.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_DOWNLOADER_NOT_EXISTS), null));
            return;
        }
        Downloader downloader = selected.get();
        List<TorrentWrapper> torrentWrappers = getServer().getLivePeersSnapshot()
                .values().stream()
                .flatMap(Collection::stream)
                .filter(p -> p.getDownloader().equals(downloader.getName()))
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

    /**
     * Handles retrieving the status of a specific downloader.
     *
     * @param ctx The Javalin context for the HTTP request
     * @param downloaderName The name of the downloader to retrieve status for
     *
     * @throws IllegalArgumentException if the downloader name is invalid
     *
     * Retrieves and returns comprehensive status information for a specified downloader, including:
     * - Last known status
     * - Status message
     * - Number of active torrents
     * - Number of active peers
     * - Downloader configuration
     * - Paused state
     *
     * Returns a 404 NOT_FOUND response if the downloader does not exist.
     * Returns a JSON response with detailed downloader status if found.
     *
     * @see DownloaderStatus
     * @see StdResp
     */
    private void handleDownloaderStatus(@NotNull Context ctx, String downloaderName) {
        String locale = locale(ctx);
        Optional<Downloader> selected = getServer().getDownloaders().stream()
                .filter(d -> d.getName().equals(downloaderName))
                .findFirst();
        if (selected.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_DOWNLOADER_NOT_EXISTS), null));
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
        ctx.json(new StdResp(true, null, new DownloaderStatus(lastStatus, tl(locale, downloader.getLastStatusMessage() == null ? new TranslationComponent(Lang.STATUS_TEXT_UNKNOWN) : downloader.getLastStatusMessage()), activeTorrents, activePeers, config, downloader.isPaused())));
    }

    /**
     * Handles the retrieval of all registered downloaders and sends them as a JSON response.
     *
     * @param ctx The Javalin context containing the HTTP request and response
     * @return A standard response containing a list of downloader wrappers with their details
     */
    private void handleDownloaderList(@NotNull Context ctx) {
        List<DownloaderWrapper> downloaders = getServer().getDownloaders()
                .stream().map(d -> new DownloaderWrapper(d.getName(), d.getEndpoint(), d.getType().toLowerCase(), d.isPaused()))
                .toList();
        ctx.json(new StdResp(true, null, downloaders));
    }


    @Override
    public void onDisable() {

    }

    record DraftDownloader(String name, JsonObject config) {
    }

    record DownloaderStatus(DownloaderLastStatus lastStatus, String lastStatusMessage,
                            long activeTorrents,
                            long activePeers, JsonObject config, boolean paused) {

    }

    record DownloaderWrapper(String name, String endpoint, String type, boolean paused) {
    }
}
