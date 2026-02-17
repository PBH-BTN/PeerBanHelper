package com.ghostchu.peerbanhelper.downloader.impl.bitcomet;

import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.peer.PeerImpl;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.bittorrent.torrent.TorrentImpl;
import com.ghostchu.peerbanhelper.bittorrent.tracker.Tracker;
import com.ghostchu.peerbanhelper.bittorrent.tracker.TrackerImpl;
import com.ghostchu.peerbanhelper.downloader.*;
import com.ghostchu.peerbanhelper.downloader.impl.bitcomet.crypto.BCAESTool;
import com.ghostchu.peerbanhelper.downloader.impl.bitcomet.resp.*;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.ByteUtil;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.traversal.NatAddressProvider;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.common.net.HostAndPort;
import com.google.gson.JsonObject;
import com.spotify.futures.CompletableFutures;
import com.vdurmont.semver4j.Semver;
import inet.ipaddr.IPAddress;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.*;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.ghostchu.peerbanhelper.text.Lang.DOWNLOADER_BC_FAILED_SAVE_BANLIST;
import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

public final class BitComet extends AbstractDownloader {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(BitComet.class);
    private static final UUID clientId = UUID.nameUUIDFromBytes("PeerBanHelper".getBytes(StandardCharsets.UTF_8));
    private final String apiEndpoint;
    private final OkHttpClient httpClient;
    private final Config config;
    private String deviceToken;
    private Semver serverVersion;
    private final ExecutorService parallelService = Executors.newWorkStealingPool();

    public BitComet(String id, Config config, AlertManager alertManager, HTTPUtil httpUtil, NatAddressProvider natAddressProvider) {
        super(id, alertManager, natAddressProvider);
        BCAESTool.init();
        this.config = config;
        this.apiEndpoint = config.getEndpoint();

        var builder = httpUtil.newBuilderForDownloader()
                .connectionPool(new ConnectionPool(getMaxConcurrentPeerRequestSlots() + 10, 5, TimeUnit.MINUTES))
                .connectTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .readTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .writeTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .addInterceptor(chain -> {
                    Request originalRequest = chain.request();
                    Request newRequest = originalRequest.newBuilder()
                            .header("Content-Type", "application/json")
                            .header("Client-Type", "BitComet WebUI")
                            .header("User-Agent", "PeerBanHelper BitComet Adapter")
                            .build();
                    return chain.proceed(newRequest);
                });
        httpUtil.disableSSLVerify(builder, !config.isVerifySsl());
        this.httpClient = builder.build();
    }

    @Override
    public @NotNull String getName() {
        return config.getName();
    }

    public static BitComet loadFromConfig(String id, ConfigurationSection section, AlertManager alertManager, HTTPUtil httpUtil, NatAddressProvider natAddressProvider) {
        Config config = Config.readFromYaml(section, id);
        return new BitComet(id, config, alertManager, httpUtil, natAddressProvider);
    }

    public static BitComet loadFromConfig(String id, JsonObject section, AlertManager alertManager, HTTPUtil httpUtil, NatAddressProvider natAddressProvider) {
        Config config = JsonUtil.getGson().fromJson(section, Config.class);
        return new BitComet(id, config, alertManager, httpUtil, natAddressProvider);
    }

    private static PeerAddress parseAddress(String address, int port, int listenPort) {
        address = address.trim();
        HostAndPort hostAndPort = HostAndPort.fromString(address);
        return new PeerAddress(hostAndPort.getHost(), hostAndPort.getPortOrDefault(port), hostAndPort.getHost());
    }

    @Override
    public @NotNull List<DownloaderFeatureFlag> getFeatureFlags() {
        List<DownloaderFeatureFlag> flags = new ArrayList<>(2);
        flags.add(DownloaderFeatureFlag.UNBAN_IP);
        flags.add(DownloaderFeatureFlag.LIVE_UPDATE_BT_PROTOCOL_PORT);
        if (serverVersion.isGreaterThanOrEqualTo("2.20")) {
            flags.add(DownloaderFeatureFlag.TRAFFIC_STATS);
        }
        flags.add(DownloaderFeatureFlag.RANGE_BAN_IP);
        return flags;
    }

    @Override
    public @NotNull JsonObject saveDownloaderJson() {
        return JsonUtil.getGson().toJsonTree(config).getAsJsonObject();
    }

    @Override
    public @NotNull YamlConfiguration saveDownloader() {
        return config.saveToYaml();
    }

    public DownloaderLoginResult login0() throws Exception {
        if (isLoggedIn())
            return new DownloaderLoginResult(DownloaderLoginResult.Status.SUCCESS, new TranslationComponent(Lang.STATUS_TEXT_OK)); // 重用 Session 会话
        Map<String, String> loginAttemptCred = new HashMap<>();
        loginAttemptCred.put("username", config.getUsername());
        loginAttemptCred.put("password", config.getPassword());
        String aesEncrypted = BCAESTool.credential(JsonUtil.standard().toJson(loginAttemptCred), clientId.toString());
        Map<String, String> loginJsonObject = new HashMap<>();
        loginJsonObject.put("authentication", aesEncrypted);
        loginJsonObject.put("client_id", clientId.toString());

        RequestBody requestBody = RequestBody.create(JsonUtil.standard().toJson(loginJsonObject), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(apiEndpoint + BCEndpoint.USER_LOGIN.getEndpoint())
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_LOGIN_EXCEPTION, response.code() + " " + response.body().string()));
            }
            var loginResponse = JsonUtil.standard().fromJson(response.body().string(), BCLoginResponse.class);
            if ("PASSWORD_ERROR".equalsIgnoreCase(loginResponse.getErrorCode())) {
                return new DownloaderLoginResult(DownloaderLoginResult.Status.INCORRECT_CREDENTIAL, new TranslationComponent(Lang.DOWNLOADER_LOGIN_EXCEPTION, loginResponse));
            }
            if (!"ok".equalsIgnoreCase(loginResponse.getErrorCode())) {
                return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_LOGIN_EXCEPTION, loginResponse));
            }
            // 进行版本检查
            boolean bcVerAcceptable = false;
            var bcSemver = new Semver(loginResponse.getVersion(), Semver.SemverType.LOOSE);
            if (bcSemver.getMajor() > 2) {
                bcVerAcceptable = true;
            } else {
                if (bcSemver.getMajor() == 2) {
                    if (bcSemver.getMinor() >= 18) {
                        bcVerAcceptable = true;
                    }
                }
            }
            if (!bcVerAcceptable) {
                return new DownloaderLoginResult(DownloaderLoginResult.Status.MISSING_COMPONENTS, new TranslationComponent(Lang.DOWNLOADER_BC_VERSION_UNACCEPTABLE, "2.18"));
            }
            // 版本检查结束
            Map<String, String> inviteTokenRetrievePayload = new HashMap<>();
            inviteTokenRetrievePayload.put("device_id", clientId.toString());
            inviteTokenRetrievePayload.put("device_name", "PeerBanHelper - BitComet Adapter");
            inviteTokenRetrievePayload.put("invite_token", loginResponse.getInviteToken());
            inviteTokenRetrievePayload.put("platform", "webui");

            RequestBody deviceTokenRequestBody = RequestBody.create(JsonUtil.standard().toJson(inviteTokenRetrievePayload), MediaType.get("application/json"));
            Request deviceTokenRequest = new Request.Builder()
                    .url(apiEndpoint + BCEndpoint.GET_DEVICE_TOKEN.getEndpoint())
                    .post(deviceTokenRequestBody)
                    .header("Authorization", "Bearer " + loginResponse.getInviteToken())
                    .build();

            try (Response deviceTokenResponse = httpClient.newCall(deviceTokenRequest).execute()) {
                if (!deviceTokenResponse.isSuccessful()) {
                    return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_LOGIN_EXCEPTION, deviceTokenResponse.code() + " " + deviceTokenResponse.body().string()));
                }
                var deviceTokenResult = JsonUtil.standard().fromJson(deviceTokenResponse.body().string(), BCDeviceTokenResult.class);
                this.deviceToken = deviceTokenResult.getDeviceToken();
                String serverId = deviceTokenResult.getServerId();
                this.serverVersion = new Semver(deviceTokenResult.getVersion(), Semver.SemverType.LOOSE);
                String serverName = deviceTokenResult.getServerName();
                if (queryNeedReConfigureIpFilter()) {
                    enableIpFilter();
                }
            }
            return new DownloaderLoginResult(DownloaderLoginResult.Status.SUCCESS, new TranslationComponent(Lang.STATUS_TEXT_OK));
        } catch (Exception e) {
            return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_LOGIN_IO_EXCEPTION, e.getClass().getName() + ": " + e.getMessage()));
        }
    }

    @Override
    public @NotNull String getEndpoint() {
        return apiEndpoint;
    }

    @Override
    public @NotNull String getType() {
        return "BitComet";
    }

    @Override
    public boolean isPaused() {
        return config.isPaused();
    }

    @Override
    public void setPaused(boolean paused) {
        super.setPaused(paused);
        config.setPaused(paused);
    }

    public boolean isLoggedIn() {
        try {
            queryNeedReConfigureIpFilter();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean queryNeedReConfigureIpFilter() throws IOException {
        RequestBody requestBody = RequestBody.create("{}", MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(apiEndpoint + BCEndpoint.GET_IP_FILTER_CONFIG.getEndpoint())
                .post(requestBody)
                .header("Authorization", "Bearer " + this.deviceToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Not a excepted statusCode while query the IPFilter status");
            }
            var resp = JsonUtil.standard().fromJson(response.body().string(), BCIpFilterResponse.class);
            boolean isBlacklistMode = false;
            if (resp.getIpFilterConfig().getFilterMode() != null) {
                isBlacklistMode = "blacklist".equals(resp.getIpFilterConfig().getFilterMode());
            }
            return !resp.getIpFilterConfig().getEnableIpFilter() || !isBlacklistMode;
        }
    }

    private void enableIpFilter() throws IOException {
        Map<String, Object> settings = new HashMap<>() {{
            put("ip_filter_config", new HashMap<>() {{
                put("enable_ipfilter", true);
                put("ipfilter_mode", "blacklist");
            }});
        }};

        RequestBody requestBody = RequestBody.create(JsonUtil.standard().toJson(settings), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(apiEndpoint + BCEndpoint.SET_IP_FILTER_CONFIG.getEndpoint())
                .post(requestBody)
                .header("Authorization", "Bearer " + this.deviceToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error(tlUI(Lang.DOWNLOADER_BC_CONFIG_IP_FILTER_FAILED));
                return;
            }
            var configResp = JsonUtil.standard().fromJson(response.body().string(), BCConfigSetResponse.class);
            if ("ok".equalsIgnoreCase(configResp.getErrorCode())) {
                log.info(tlUI(Lang.DOWNLOADER_BC_CONFIG_IP_FILTER_SUCCESS));
            } else {
                log.error(tlUI(Lang.DOWNLOADER_BC_CONFIG_IP_FILTER_FAILED));
            }
        }
    }

    @Override
    public @NotNull List<Torrent> getTorrents() {
        Map<String, Object> requirements = new HashMap<>();
        requirements.put("state_group", "ACTIVE");
        requirements.put("sort_key", "");
        requirements.put("sort_order", "unsorted");
        requirements.put("tag_filter", "ALL");
        requirements.put("task_type", "ALL");
        requirements.put("start", 0);
        requirements.put("limit", Integer.MAX_VALUE - 1);
        return fetchTorrents(requirements, !config.isIgnorePrivate());
    }

    @Override
    public @NotNull List<Torrent> getAllTorrents() {
        Map<String, Object> requirements = new HashMap<>();
        requirements.put("state_group", "ALL");
        requirements.put("sort_key", "");
        requirements.put("sort_order", "unsorted");
        requirements.put("tag_filter", "ALL");
        requirements.put("task_type", "ALL");
        requirements.put("start", 0);
        requirements.put("limit", Integer.MAX_VALUE - 1);
        return fetchTorrents(requirements, true);
    }


    public List<Torrent> fetchTorrents(Map<String, Object> requirements, boolean includePrivate) {
        RequestBody requestBody = RequestBody.create(JsonUtil.standard().toJson(requirements), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(apiEndpoint + BCEndpoint.GET_TASK_LIST.getEndpoint())
                .post(requestBody)
                .header("Authorization", "Bearer " + this.deviceToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException(tlUI(Lang.DOWNLOADER_BC_FAILED_REQUEST_TORRENT_LIST, response.code(), response.body().string()));
            }
            var taskListResponse = JsonUtil.standard().fromJson(response.body().string(), BCTaskListResponse.class);

            Semaphore semaphore = new Semaphore(3);
            List<BCTaskTorrentResponse> torrentResponses = CompletableFutures.allAsList(taskListResponse.getTasks().stream()
                    .filter(t -> "BT".equals(t.getType()))
                    .map(torrent -> CompletableFuture.supplyAsync(() -> {
                        try {
                            semaphore.acquire();
                            Map<String, String> taskIds = new HashMap<>();
                            taskIds.put("task_id", String.valueOf(torrent.getTaskId()));
                            RequestBody taskRequestBody = RequestBody.create(JsonUtil.standard().toJson(taskIds), MediaType.get("application/json"));
                            Request taskRequest = new Request.Builder()
                                    .url(apiEndpoint + BCEndpoint.GET_TASK_SUMMARY.getEndpoint())
                                    .post(taskRequestBody)
                                    .header("Authorization", "Bearer " + this.deviceToken)
                                    .build();

                            try (Response taskResponse = httpClient.newCall(taskRequest).execute()) {
                                if (!taskResponse.isSuccessful()) {
                                    log.warn(tlUI(Lang.DOWNLOADER_BITCOMET_UNABLE_FETCH_TASK_SUMMARY));
                                    return null;
                                }
                                return JsonUtil.standard().fromJson(taskResponse.body().string(), BCTaskTorrentResponse.class);
                            }
                        } catch (IOException | InterruptedException e) {
                            log.warn(tlUI(Lang.DOWNLOADER_BITCOMET_UNABLE_FETCH_TASK_SUMMARY), e);
                            if (e instanceof InterruptedException) {
                                Thread.currentThread().interrupt();
                            }
                            return null;
                        } finally {
                            semaphore.release();
                        }
                    }, parallelService)).toList()).join();
            return torrentResponses.stream()
                    .filter(Objects::nonNull)
                    .map(torrent -> new TorrentImpl(Long.toString(torrent.getTask().getTaskId()),
                            torrent.getTask().getTaskName(),
                            torrent.getTaskDetail().getInfohash() != null ? torrent.getTaskDetail().getInfohash() : torrent.getTaskDetail().getInfohashV2(),
                            torrent.getTaskDetail().getTotalSize(),
                            torrent.getTask().getSelectedDownloadedSize(),
                            torrent.getTaskStatus().getDownloadPermillage() / 1000.0d,
                            torrent.getTask().getUploadRate(),
                            torrent.getTask().getDownloadRate(),
                            torrent.getTaskDetail().getTorrentPrivate()
                    ))
                    .filter(torrent -> includePrivate || !torrent.isPrivate())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public @NotNull List<Tracker> getTrackers(@NotNull Torrent torrent) {
        Map<String, Object> requirements = new HashMap<>();
        requirements.put("task_id", torrent.getId());
        requirements.put("max_count", String.valueOf(Integer.MAX_VALUE));

        RequestBody requestBody = RequestBody.create(JsonUtil.standard().toJson(requirements), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(apiEndpoint + BCEndpoint.GET_TASK_TRACKERS.getEndpoint())
                .post(requestBody)
                .header("Authorization", "Bearer " + this.deviceToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException(tlUI(Lang.DOWNLOADER_BC_FAILED_REQUEST_PEERS_LIST_IN_TORRENT, response.code(), response.body().string()));
            }
            var trackers = JsonUtil.standard().fromJson(response.body().string(), BCTaskTrackersResponse.class);
            return trackers.getTrackers().stream()
                    .filter(t -> t.getName().startsWith("http") || t.getName().startsWith("udp") || t.getName().startsWith("ws"))
                    .map(t -> (Tracker) new TrackerImpl(t.getName())).toList();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void setTrackers(@NotNull Torrent torrent, @NotNull List<Tracker> trackers) {
        // Unsupported Operation
    }

    @Override
    public @NotNull DownloaderStatistics getStatistics() {
        long totalUploaded = 0;
        long totalDownloaded = 0;
        Map<String, Object> body = new HashMap<>();
        body.put("token_list", List.of(
                "${G_TOTAL_DOWNLOAD_AUTO}", "${G_TOTAL_UPLOAD_AUTO}", "${G_SESSION_DOWNLOAD_AUTO}", "${G_SESSION_UPLOAD_AUTO}",
                "${G_TOTAL_DOWNLOAD_BYTE}", "${G_TOTAL_UPLOAD_BYTE}", "${G_SESSION_DOWNLOAD_BYTE}", "${G_SESSION_UPLOAD_BYTE}"));
        RequestBody requestBody = RequestBody.create(JsonUtil.standard().toJson(body), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(apiEndpoint + BCEndpoint.GET_STATISTICS_LIST.getEndpoint())
                .post(requestBody)
                .header("Authorization", "Bearer " + this.deviceToken)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            var respBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new IllegalStateException(tlUI(Lang.DOWNLOADER_BC_FAILED_REQUEST_STATISTICS_LIST, response.code(), respBody));
            }
            var valueList = JsonUtil.standard().fromJson(respBody, BCStatisticsValueListResponse.class);
            for (BCStatisticsValueListResponse.ValueListDTO valueListDTO : valueList.getValueList()) {
                if (serverVersion.isGreaterThanOrEqualTo("2.20")) {
                    switch (valueListDTO.getToken()) {
                        case "${G_TOTAL_DOWNLOAD_BYTE}" -> totalDownloaded = Long.parseLong(valueListDTO.getValue());
                        case "${G_TOTAL_UPLOAD_BYTE}" -> totalUploaded = Long.parseLong(valueListDTO.getValue());
                    }
                } else {
                    switch (valueListDTO.getToken()) {
                        case "${G_TOTAL_DOWNLOAD_AUTO}" ->
                                totalDownloaded = readHumanReadableValue(valueListDTO.getValue());
                        case "${G_TOTAL_UPLOAD_AUTO}" ->
                                totalUploaded = readHumanReadableValue(valueListDTO.getValue());
                    }
                }

            }
        } catch (Exception e) {
            log.warn("Failed to fetch BitComet statistics", e);
            throw new IllegalStateException(tlUI(Lang.DOWNLOADER_BC_FAILED_REQUEST_STATISTICS_LIST, "N/A", "N/A", e));

        }
        return new DownloaderStatistics(totalUploaded, totalDownloaded);
    }

    private long readHumanReadableValue(String value) {
        // value = 2.33 GB etc.
        // return bytes
        String[] parts = value.split(" ");
        if (parts.length != 2) {
            return 0;
        }
        double number;
        try {
            number = Double.parseDouble(parts[0]);
        } catch (NumberFormatException e) {
            return 0;
        }
        String unit = parts[1].toUpperCase();
        return switch (unit) {
            case "B" -> (long) number;
            case "KB" -> (long) (number * 1024);
            case "MB" -> (long) (number * 1024 * 1024);
            case "GB" -> (long) (number * 1024 * 1024 * 1024);
            case "TB" -> (long) (number * 1024L * 1024L * 1024L * 1024L);
            case "PB" -> (long) (number * 1024L * 1024L * 1024L * 1024L * 1024L);
            case "EB" -> (long) (number * 1024L * 1024L * 1024L * 1024L * 1024L * 1024L);
            case "ZB" -> (long) (number * 1024L * 1024L * 1024L * 1024L * 1024L * 1024L * 1024L);
            default -> 0;
        };

    }

    @Override
    public @NotNull List<Peer> getPeers(@NotNull Torrent torrent) {
        Map<String, Object> requirements = new HashMap<>();
        requirements.put("groups", List.of("peers_connected")); // 2.11 Beta 3 可以限制获取哪一类 Peers，注意下面仍需要检查，因为旧版本不支持
        requirements.put("task_id", torrent.getId());
        requirements.put("max_count", String.valueOf(Integer.MAX_VALUE)); // 获取全量列表，因为我们需要检查所有 Peers

        RequestBody requestBody = RequestBody.create(JsonUtil.standard().toJson(requirements), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(apiEndpoint + BCEndpoint.GET_TASK_PEERS.getEndpoint())
                .post(requestBody)
                .header("Authorization", "Bearer " + this.deviceToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException(tlUI(Lang.DOWNLOADER_BC_FAILED_REQUEST_PEERS_LIST_IN_TORRENT, response.code(), response.body().string()));
            }
            var peers = JsonUtil.standard().fromJson(response.body().string(), BCTaskPeersResponse.class);
            if (peers.getPeers() == null) {
                return Collections.emptyList();
            }
            var noGroupField = peers.getPeers().stream().noneMatch(dto -> dto.getGroup() != null); // 2.10 的一些版本没有 group 字段
            var stream = peers.getPeers().stream();

            if (!noGroupField) { // 对于新版本，添加一个 group 过滤
                stream = stream.filter(dto -> "connected".equals(dto.getGroup()) // 2.10 正式版
                        || "connected_peers".equals(dto.getGroup()) // 2.11 Beta 1-2
                        || "peers_connected".equals(dto.getGroup())); // 2.11 Beta 3
            }
            return stream.map(peer -> new PeerImpl(
                    natTranslate(parseAddress(peer.getIp(), peer.getRemotePort(), peer.getListenPort())),

                    ByteUtil.hexToByteArray(peer.getPeerId()),
                    peer.getClientType(),
                    peer.getDlRate(),
                    peer.getDlSize() != null ? peer.getDlSize() : -1, // 兼容 2.10
                    peer.getUpRate(),
                    peer.getUpSize() != null ? peer.getUpSize() : -1, // 兼容 2.10
                    peer.getPermillage() / 1000.0d, null,
                    peer.getDlRate() <= 0 && peer.getUpRate() <= 0)
            ).collect(Collectors.toList());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void setBanList(@NotNull Collection<IPAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed, boolean applyFullList) {
        if (removed != null && !removed.isEmpty()) {
            unbanPeers(removed.stream().map(meta -> meta.getPeer().getAddress().toString()).toList());
        }
        setBanListFull(fullList);
    }

    private void setBanListFull(Collection<IPAddress> peerAddresses) {
        String banStr = peerAddresses.stream()
                .map(ipAddr -> remapBanListAddress(ipAddr).toNormalizedString())
                .collect(Collectors.joining("\n"));
        operateBanListLegacy("replace", banStr);
    }


    private void unbanPeers(List<String> peerAddresses) {
        Map<String, Object> banListSettings = new HashMap<>();
        banListSettings.put("ip_list", peerAddresses);
        banListSettings.put("unban_range", "unban_peers_in_all_tasks");

        RequestBody requestBody = RequestBody.create(JsonUtil.standard().toJson(banListSettings), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(apiEndpoint + BCEndpoint.TASK_UNBAN_PEERS.getEndpoint())
                .post(requestBody)
                .header("Authorization", "Bearer " + this.deviceToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error(tlUI(DOWNLOADER_BC_FAILED_SAVE_BANLIST, getName(), apiEndpoint, response.code(), "HTTP ERROR (unban_peers)", response.body().string()));
                throw new IllegalStateException("Save BitComet banlist error: statusCode=" + response.code());
            }
        } catch (Exception e) {
            log.error(tlUI(DOWNLOADER_BC_FAILED_SAVE_BANLIST, getName(), apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
            throw new IllegalStateException(e);
        }
    }

    private void operateBanListLegacy(String mode, String content) {
        Map<String, String> banListSettings = new HashMap<>();
        banListSettings.put("import_type", mode);
        banListSettings.put("data_type", "data_file");
        banListSettings.put("content_base64", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));

        RequestBody requestBody = RequestBody.create(JsonUtil.standard().toJson(banListSettings), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(apiEndpoint + BCEndpoint.IP_FILTER_UPLOAD.getEndpoint())
                .post(requestBody)
                .header("Authorization", "Bearer " + this.deviceToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error(tlUI(DOWNLOADER_BC_FAILED_SAVE_BANLIST, getName(), apiEndpoint, response.code(), "HTTP ERROR", response.body().string()));
                throw new IllegalStateException("Save BitComet banlist error: statusCode=" + response.code());
            }
        } catch (Exception e) {
            log.error(tlUI(DOWNLOADER_BC_FAILED_SAVE_BANLIST, getName(), apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        if (!parallelService.isShutdown()) {
            parallelService.shutdownNow();
        }
    }

    @Override
    public int getMaxConcurrentPeerRequestSlots() {
        return 4;
    }

    /**
     * 获取当前下载器的限速配置
     *
     * @return 限速配置
     */
    @Override
    public DownloaderSpeedLimiter getSpeedLimiter() {
        try {
            Map<String, Object> map = new HashMap<>();
            RequestBody requestBody = RequestBody.create(JsonUtil.standard().toJson(map), MediaType.get("application/json"));
            Request request = new Request.Builder()
                    .url(apiEndpoint + BCEndpoint.GET_CONNECTION_CONFIG.getEndpoint())
                    .post(requestBody)
                    .header("Authorization", "Bearer " + this.deviceToken)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error(tlUI(Lang.DOWNLOADER_FAILED_RETRIEVE_SPEED_LIMITER, getName(), response.code() + " " + response.body().string()));
                    return null;
                }
                var configResp = JsonUtil.standard().fromJson(response.body().string(), BCConnectionConfigResponse.class);
                if (!"ok".equalsIgnoreCase(configResp.getErrorCode())) {
                    log.error(tlUI(Lang.DOWNLOADER_FAILED_RETRIEVE_SPEED_LIMITER, getName(), configResp.getErrorMessage()));
                }
                return new DownloaderSpeedLimiter(configResp.getConnectionConfig().getMaxUploadSpeed(), configResp.getConnectionConfig().getMaxDownloadSpeed());
            }
        } catch (Exception e) {
            log.error(tlUI(Lang.DOWNLOADER_FAILED_RETRIEVE_SPEED_LIMITER, getName(), e.getClass().getName() + ": " + e.getMessage()), e);
            return null;
        }
    }

    /**
     * 设置当前下载器的限速配置
     *
     * @param speedLimiter 限速配置
     */
    @Override
    public void setSpeedLimiter(@NotNull DownloaderSpeedLimiter speedLimiter) {
        try {
            Map<String, Object> map = new HashMap<>();
            Map<String, Long> connectionConfig = new HashMap<>();
            connectionConfig.put("max_download_speed", speedLimiter.isDownloadUnlimited() ? 0 : speedLimiter.download());
            connectionConfig.put("max_upload_speed", speedLimiter.isUploadUnlimited() ? 0 : speedLimiter.upload());
            map.put("connection_config", connectionConfig);

            RequestBody requestBody = RequestBody.create(JsonUtil.standard().toJson(map), MediaType.get("application/json"));
            Request request = new Request.Builder()
                    .url(apiEndpoint + BCEndpoint.SET_CONNECTION_CONFIG.getEndpoint())
                    .post(requestBody)
                    .header("Authorization", "Bearer " + this.deviceToken)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error(tlUI(Lang.DOWNLOADER_FAILED_SET_SPEED_LIMITER, getName(), response.code() + " " + response.body().string()));
                    return;
                }
                var configResp = JsonUtil.standard().fromJson(response.body().string(), BCConfigSetResponse.class);
                if (!"ok".equalsIgnoreCase(configResp.getErrorCode())) {
                    log.error(tlUI(Lang.DOWNLOADER_FAILED_SET_SPEED_LIMITER, getName(), configResp.getErrorMessage()));
                }
            }
        } catch (Exception e) {
            log.error(tlUI(Lang.DOWNLOADER_FAILED_SET_SPEED_LIMITER, getName(), e.getClass().getName() + ": " + e.getMessage()), e);
        }
    }

    @Override
    public int getBTProtocolPort() {
        try {
            Map<String, Object> map = new HashMap<>();
            RequestBody requestBody = RequestBody.create(JsonUtil.standard().toJson(map), MediaType.get("application/json"));
            Request request = new Request.Builder()
                    .url(apiEndpoint + BCEndpoint.GET_CONNECTION_CONFIG.getEndpoint())
                    .post(requestBody)
                    .header("Authorization", "Bearer " + this.deviceToken)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error(tlUI(Lang.DOWNLOADER_FAILED_RETRIEVE_BT_PROTOCOL_PORT, getName(), response.code() + " " + response.body().string()));
                    return -1;
                }
                var configResp = JsonUtil.standard().fromJson(response.body().string(), BCConnectionConfigResponse.class);
                if (!"ok".equalsIgnoreCase(configResp.getErrorCode())) {
                    log.error(tlUI(Lang.DOWNLOADER_FAILED_RETRIEVE_SPEED_LIMITER, getName(), configResp.getErrorMessage()));
                }
                return configResp.getConnectionConfig().getListenPortTcp();
            }
        } catch (Exception e) {
            log.error(tlUI(Lang.DOWNLOADER_FAILED_RETRIEVE_BT_PROTOCOL_PORT, getName(), e.getClass().getName() + ": " + e.getMessage()), e);
            return -1;
        }
    }

    @Override
    public void setBTProtocolPort(int port) {
        Map<String, Object> settings = new HashMap<>() {{
            put("connection_config", new HashMap<>() {{
                put("enable_listen_tcp", true);
                put("listen_port_tcp", port);
            }});
        }};

        RequestBody requestBody = RequestBody.create(JsonUtil.standard().toJson(settings), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(apiEndpoint + BCEndpoint.SET_CONNECTION_CONFIG.getEndpoint())
                .post(requestBody)
                .header("Authorization", "Bearer " + this.deviceToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error(tlUI(Lang.DOWNLOADER_BC_CONFIG_IP_FILTER_FAILED));
                return;
            }
            var json = response.body().string();
            var configResp = JsonUtil.standard().fromJson(json, BCConfigSetResponse.class);
            if (!"ok".equalsIgnoreCase(configResp.getErrorCode())) {
                log.error(tlUI(Lang.DOWNLOADER_FAILED_SAVE_BT_PROTOCOL_PORT));
                //System.out.println(json);
                throw new IllegalStateException("Failed to save BitComet BT Protocol Port: " + configResp.getErrorMessage());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save BitComet BT Protocol Port", e);
        }
    }

    @NoArgsConstructor
    @Data
    public static class Config {
        private String name;
        private String type;
        private String endpoint;
        private String username;
        private String password;
        private boolean incrementBan;
        private boolean verifySsl;
        private boolean ignorePrivate;
        private boolean paused;

        public static Config readFromYaml(ConfigurationSection section, String alternativeName) {
            Config config = new Config();
            config.setType("bitcomet");
            config.setName(section.getString("name", alternativeName));
            config.setEndpoint(section.getString("endpoint"));
            if (config.getEndpoint().endsWith("/")) { // 浏览器复制党 workaround 一下， 避免连不上的情况
                config.setEndpoint(config.getEndpoint().substring(0, config.getEndpoint().length() - 1));
            }
            config.setUsername(section.getString("username", ""));
            config.setPassword(section.getString("password", ""));
            config.setIncrementBan(section.getBoolean("increment-ban", true));
            config.setVerifySsl(section.getBoolean("verify-ssl", true));
            config.setIgnorePrivate(section.getBoolean("ignore-private", false));
            config.setPaused(section.getBoolean("paused", false));
            return config;
        }

        public YamlConfiguration saveToYaml() {
            YamlConfiguration section = new YamlConfiguration();
            section.set("type", "bitcomet");
            section.set("name", name);
            section.set("endpoint", endpoint);
            section.set("username", username);
            section.set("password", password);
            section.set("increment-ban", incrementBan);
            section.set("verify-ssl", verifySsl);
            section.set("ignore-private", ignorePrivate);
            section.set("paused", paused);
            return section;
        }
    }
}
