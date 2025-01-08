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
     * Indicates whether this module is configurable.
     *
     * @return Always returns {@code false}, signifying that this module does not support runtime configuration changes.
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
     * @param ctx the Javalin context containing the request details
     * @throws IllegalArgumentException if the downloader name contains illegal characters
     *
     * This method processes a JSON payload to create a new downloader with the following steps:
     * 1. Parse the request body as a JSON object
     * 2. Validate the downloader name (no periods allowed)
     * 3. Extract configuration and optional paused state
     * 4. Attempt to create the downloader using the server's create method
     * 5. Register the downloader if creation is successful
     * 6. Save the updated downloader configuration
     *
     * Possible HTTP responses:
     * - 201 Created: Downloader successfully created
     * - 400 Bad Request: Downloader creation failed (invalid config or already exists)
     * - 500 Internal Server Error: Unable to save downloader configuration
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
     * Handles the HTTP PATCH request to update an existing downloader's configuration.
     *
     * @param ctx The Javalin context containing the request details
     * @param downloaderName The current name of the downloader to be updated
     * @throws IllegalArgumentException If the new downloader name contains an illegal character (.)
     *
     * This method performs the following operations:
     * 1. Parses the request body as a JSON object
     * 2. Validates the new downloader name
     * 3. Extracts configuration and paused state
     * 4. Creates a new downloader with the updated configuration
     * 5. Unregisters the old downloader
     * 6. Attempts to register the new downloader
     * 7. Saves the updated downloader configuration
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
     * Handles testing a downloader configuration by creating a temporary downloader and attempting to log in.
     *
     * @param ctx The Javalin context containing the request details
     * @throws IllegalArgumentException If the downloader name contains illegal characters
     *
     * @apiNote This method performs the following steps:
     * - Parses the request body to extract downloader configuration
     * - Validates the downloader name
     * - Creates a temporary downloader instance
     * - Attempts to log in if the downloader is not paused
     * - Returns a standardized response indicating test success or failure
     *
     * @see Downloader#login()
     * @see StdResp
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
     * This method filters the live peers snapshot to find peers matching the specified downloader
     * and torrent ID, sorts them by upload speed in descending order, and optionally resolves
     * their PTR (Reverse DNS) records based on configuration.
     *
     * @param ctx The Javalin context containing the HTTP request details
     * @param downloaderName The name of the downloader to retrieve peers from
     * @param torrentId The unique identifier of the torrent to filter peers
     *
     * @throws IllegalArgumentException If the downloader or torrent cannot be found
     *
     * @apiNote Performs the following steps:
     * 1. Validates the existence of the specified downloader
     * 2. Retrieves DNS reverse lookup configuration
     * 3. Filters peers by downloader and torrent
     * 4. Sorts peers by upload speed
     * 5. Populates peer details with optional PTR resolution
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
     * Populates a PeerDTO with additional metadata about a peer.
     *
     * @param p The peer metadata to process
     * @param resolvePTR Flag indicating whether to resolve the PTR (Reverse DNS) record
     * @return A populated PeerDTO with geographic and DNS information
     *
     * This method enriches peer information by:
     * - Retrieving geographic data from the IP database
     * - Optionally resolving the PTR record using either a DNS lookup experiment or standard Java DNS resolution
     *
     * @throws UnknownHostException If DNS resolution fails using standard Java method
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
     * Retrieves and returns a list of torrents associated with a specific downloader.
     *
     * This method filters live peers by the specified downloader name, extracts unique torrents,
     * and sorts them primarily by upload speed and secondarily by download speed in descending order.
     *
     * @param ctx The Javalin context containing the HTTP request details
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
     * Retrieves and returns the status of a specific downloader.
     *
     * This method handles the HTTP request to fetch detailed status information about a downloader,
     * including its last status, active torrents, active peers, configuration, and paused state.
     *
     * @param ctx The Javalin context containing the HTTP request details
     * @param downloaderName The name of the downloader to retrieve status for
     *
     * @throws IllegalArgumentException if the downloader name is invalid or not found
     *
     * @apiNote Returns a JSON response with:
     * - HTTP 404 if downloader is not found
     * - HTTP 200 with downloader status details if found
     *
     * @see Downloader
     * @see DownloaderStatus
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
     * Handles the HTTP request to retrieve a list of all registered downloaders.
     *
     * @param ctx The Javalin context representing the HTTP request and response
     * 
     * @apiNote This method returns a standardized JSON response containing:
     * - A list of downloaders with their name, endpoint, type, and paused status
     * - Successful response with HTTP status 200 if downloaders are retrieved
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
