package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.bittorrent.tracker.Tracker;
import com.ghostchu.peerbanhelper.bittorrent.tracker.TrackerImpl;
import com.ghostchu.peerbanhelper.downloader.*;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.*;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.traversal.NatAddressProvider;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.vdurmont.semver4j.Semver;
import inet.ipaddr.Address;
import inet.ipaddr.IPAddress;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public abstract class AbstractQbittorrent extends AbstractDownloader {
    protected final String apiEndpoint;
    protected final OkHttpClient httpClient;
    protected final QBittorrentConfig config;
    protected final Cache<String, TorrentProperties> torrentPropertiesCache;
    protected final ExecutorService parallelService = Executors.newWorkStealingPool();
    protected Semver lastSemver = new Semver("0.0.0");

    public AbstractQbittorrent(String id, QBittorrentConfig config, AlertManager alertManager, HTTPUtil httpUtil, NatAddressProvider natAddressProvider) {
        super(id, alertManager, natAddressProvider);
        this.config = config;
        this.apiEndpoint = config.getEndpoint() + "/api/v2";

        var builder = httpUtil.newBuilderForDownloader()
                .connectionPool(new ConnectionPool(getMaxConcurrentPeerRequestSlots() + 10, 5, TimeUnit.MINUTES))
                .connectTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .readTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .writeTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .authenticator((route, response) -> {
                    if (HTTPUtil.responseCount(response) > 1) {
                        return null;
                    }
                    String credential = Credentials.basic(config.getBasicAuth().getUser(), config.getBasicAuth().getPass());
                    return response.request().newBuilder()
                            .header("Authorization", credential)
                            .build();
                });
        httpUtil.disableSSLVerify(builder, !config.isVerifySsl());
        this.httpClient = builder.build();

        YamlConfiguration profileConfig = Main.getProfileConfig();
        this.torrentPropertiesCache = CacheBuilder.newBuilder()
                //.maximumSize(2000)
                .expireAfterAccess(
                        profileConfig.getLong("check-interval", 5000) + (1000 * 60),
                        TimeUnit.MILLISECONDS
                )
                .build();
    }

    @Override
    public int getMaxConcurrentPeerRequestSlots() {
        // qBittorrent Http Server 的 Connection Limit 是 500
        // https://github.com/qbittorrent/qBittorrent/blob/3de2a9f486a77a911fab986daabbe50ce7c85b04/src/base/http/server.cpp#L57
        return ExternalSwitch.parseInt("pbh.downloader.qBittorrent.maxConcurrentPeerRequestSlots", 128);
    }


    @Override
    public @NotNull String getName() {
        return config.getName();
    }

    @Override
    public @NotNull JsonObject saveDownloaderJson() {
        return JsonUtil.getGson().toJsonTree(config).getAsJsonObject();
    }

    @Override
    public @NotNull YamlConfiguration saveDownloader() {
        return config.saveToYaml();
    }

    public DownloaderLoginResult login0() {
        if (isLoggedIn())
            return new DownloaderLoginResult(DownloaderLoginResult.Status.SUCCESS, new TranslationComponent(Lang.STATUS_TEXT_OK)); // 重用 Session 会话
        try {
            FormBody formBody = new FormBody.Builder()
                    .add("username", config.getUsername())
                    .add("password", config.getPassword())
                    .build();

            Request request = new Request.Builder()
                    .url(apiEndpoint + "/auth/login")
                    .post(formBody)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && isLoggedIn()) {
                    updateAndApplyMetadataIfRequired();
                    if (lastSemver.isGreaterThanOrEqualTo(new Semver("4.5.0")) || ExternalSwitch.parseBoolean("pbh.downloader.qBittorrent.bypassVersionCheck", false)) {
                        return new DownloaderLoginResult(DownloaderLoginResult.Status.SUCCESS, new TranslationComponent(Lang.STATUS_TEXT_OK));
                    } else {
                        return new DownloaderLoginResult(DownloaderLoginResult.Status.REQUIRE_TAKE_ACTIONS, new TranslationComponent(Lang.DOWNLOADER_VERSION_INCOMPATIBLE, lastSemver.toString(), ">= 4.5.0"));
                    }
                }
                return new DownloaderLoginResult(DownloaderLoginResult.Status.INCORRECT_CREDENTIAL, new TranslationComponent(Lang.DOWNLOADER_LOGIN_EXCEPTION, response.body().string()));
            }
        } catch (Exception e) {
            return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_LOGIN_IO_EXCEPTION, e.getClass().getName() + ": " + e.getMessage()));
        }
    }

    @NotNull
    private Semver getDownloaderVersion() {
        try (Response response = httpClient.newCall(new Request.Builder()
                .url(apiEndpoint + "/app/version")
                .get()
                .build()).execute()) {
            if (response.isSuccessful()) {
                String versionStr = response.body().string().trim();
                if (versionStr.startsWith("v")) {
                    versionStr = versionStr.substring(1);
                }
                return new Semver(normalizeVersion(versionStr), Semver.SemverType.LOOSE);
            } else {
                throw new IllegalStateException("Failed to get qBittorrent version: statusCode=" + response.code());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to get qBittorrent version", e);
        }
    }

    public void updatePreferences() {
        FormBody.Builder formBody = new FormBody.Builder();
        try {
            if (ExternalSwitch.parseBoolean("pbh.downloader.qbittorrent.disableSameIpMultiConnection", true)) {
                formBody.add("json", JsonUtil.getGson().toJson(Map.of("enable_multi_connections_from_same_ip", false)));
            }
            Request request = new Request.Builder()
                    .url(apiEndpoint + "/app/setPreferences")
                    .post(formBody.build())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error(tlUI(Lang.DOWNLOADER_QB_DISABLE_SAME_IP_MULTI_CONNECTION_FAILED, getName(), apiEndpoint, response.code(), "HTTP ERROR", response.body().string()));
                    throw new IllegalStateException("Save qBittorrent preferences error: statusCode=" + response.code());
                }
            }
        } catch (Exception e) {
            log.error(tlUI(Lang.DOWNLOADER_QB_DISABLE_SAME_IP_MULTI_CONNECTION_FAILED, getName(), apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
            throw new IllegalStateException(e);
        }
    }


    @Override
    public @NotNull String getEndpoint() {
        return apiEndpoint;
    }


    public boolean isLoggedIn() {
        try {
            Request request = new Request.Builder()
                    .url(apiEndpoint + "/app/buildInfo")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return false;
                }
                QBittorrentBuildInfo info = JsonUtil.getGson().fromJson(response.body().string(), QBittorrentBuildInfo.class);
                if (info == null) {
                    return false;
                }
                if (info.getLibtorrent() == null) {
                    return false;
                }
                boolean loggedIn = !info.getLibtorrent().isBlank();
                if (loggedIn) {
                    updateAndApplyMetadataIfRequired();
                }
                return loggedIn;
            }
        } catch (Exception e) {
            return false;
        }
    }


    public void updateAndApplyMetadataIfRequired() {
        if (getLastStatus() != DownloaderLastStatus.HEALTHY) {
            updatePreferences();
            this.lastSemver = getDownloaderVersion();
        }
    }

    @Override
    public void setBanList(@NotNull Collection<IPAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed, boolean applyFullList) {
        if (removed != null && removed.isEmpty() && added != null && config.isIncrementBan() && !applyFullList) {
            setBanListIncrement(added);
        } else {
            setBanListFull(fullList);
        }
    }

    @Override
    public @NotNull List<Torrent> getTorrents() {
        return fetchTorrents(true, !config.isIgnorePrivate());
    }

    @Override
    public @NotNull List<Torrent> getAllTorrents() {
        return fetchTorrents(false, true);
    }

    private List<Torrent> fetchTorrents(boolean onlyActive, boolean includePrivate) {
        List<QBittorrentTorrent> allTorrents = new ArrayList<>();
        Set<String> seenHashes = new HashSet<>(); // 用于检测重复
        int pageSize = 100; // 每页大小
        int offset = 0;

        while (true) {
            try {
                String url = apiEndpoint + "/torrents/info";
                if (onlyActive) {
                    url += "?filter=active";
                }
                url += (url.contains("?") ? "&" : "?") + "limit=" + pageSize + "&offset=" + offset;

                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IllegalStateException(tlUI(Lang.DOWNLOADER_QB_FAILED_REQUEST_TORRENT_LIST, response.code(), response.body() != null ? response.body().string() : "null"));
                    }
                    String responseBody = response.body().string();
                    List<QBittorrentTorrent> pageTorrents = JsonUtil.getGson().fromJson(responseBody, new TypeToken<List<QBittorrentTorrent>>() {
                    }.getType());

                    if (pageTorrents == null || pageTorrents.isEmpty()) break;

                    boolean hasNew = false;
                    for (QBittorrentTorrent t : pageTorrents) {
                        if (seenHashes.add(t.getHash())) {
                            allTorrents.add(t);
                            hasNew = true;
                        }
                    }
                    // 全部重复 或者 小于分页大小
                    if (!hasNew || pageTorrents.size() < pageSize) break;

                    offset += pageSize;
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        fillTorrentProperties(allTorrents);

        return allTorrents.stream()
                .map(t -> (Torrent) t)
                .filter(t -> includePrivate || !t.isPrivate())
                .collect(Collectors.toList());
    }

    @Override
    public @NotNull List<DownloaderFeatureFlag> getFeatureFlags() {
        List<DownloaderFeatureFlag> flags = new ArrayList<>();
        flags.add(DownloaderFeatureFlag.UNBAN_IP);
        flags.add(DownloaderFeatureFlag.TRAFFIC_STATS);
        flags.add(DownloaderFeatureFlag.LIVE_UPDATE_BT_PROTOCOL_PORT);
        if ((lastSemver.isGreaterThanOrEqualTo("5.3.0")
                || lastSemver.isGreaterThanOrEqualTo("5.3.0-alpha1")
                || lastSemver.isEqualTo("5.2.0-beta1")
                || ExternalSwitch.parseBoolean("pbh.downloader.qBittorrent.ignoreRangeBanIpCondition", false))
                && ExternalSwitch.parseBoolean("pbh.downloader.qBittorrent.enableRangeBanIp", true)) {
            flags.add(DownloaderFeatureFlag.RANGE_BAN_IP);
        }
        return flags;
    }

    @Override
    public @NotNull List<Tracker> getTrackers(@NotNull Torrent torrent) {
        try {
            Request request = new Request.Builder()
                    .url(apiEndpoint + "/torrents/trackers?hash=" + torrent.getId())
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IllegalStateException(tlUI(Lang.DOWNLOADER_FAILED_REQUEST_TRACKER_LIST_ON_TORRENT, torrent.getHash(), response.code(), response.body() != null ? response.body().string() : "null"));
                }
                String responseBody = response.body().string();
                List<QBittorrentTorrentTrackers> qbTorrentTrackers = JsonUtil.getGson().fromJson(responseBody, new TypeToken<List<QBittorrentTorrentTrackers>>() {
                }.getType());
                qbTorrentTrackers = qbTorrentTrackers.stream()
                        .filter(t -> !t.getUrl().startsWith("**"))
                        .sorted(Comparator.comparingInt(QBittorrentTorrentTrackers::getTier)).toList();

                Map<Integer, List<String>> trackerMap = new HashMap<>();
                for (QBittorrentTorrentTrackers qbTorrentTracker : qbTorrentTrackers) {
                    trackerMap.computeIfAbsent(qbTorrentTracker.getTier(), k -> new ArrayList<>()).add(qbTorrentTracker.getUrl());
                }
                List<Tracker> trackers = new ArrayList<>();
                trackerMap.forEach((k, v) -> trackers.add(new TrackerImpl(v)));
                return trackers;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void setTrackers(@NotNull Torrent torrent, @NotNull List<Tracker> trackers) {
        List<Tracker> trackerList = getTrackers(torrent);
        removeTracker(torrent, trackerList);
        addTracker(torrent, trackers);
    }

    /**
     * 获取当前下载器的限速配置
     *
     * @return 限速配置，如果不支持或者请求错误，则可能返回 null
     */
    @Override
    public @Nullable DownloaderSpeedLimiter getSpeedLimiter() {
        try {
            Request request = new Request.Builder()
                    .url(apiEndpoint + "/app/preferences")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("Request failed with code: " + response.code());
                }
                String responseBody = response.body().string();
                QBittorrentPreferences preferences = JsonUtil.getGson()
                        .fromJson(responseBody, QBittorrentPreferences.class);
                long downloadLimit = preferences.getDlLimit(); // 单位是 bytes
                long uploadLimit = preferences.getUpLimit();
                return new DownloaderSpeedLimiter(uploadLimit, downloadLimit);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 设置当前下载器的限速配置
     *
     * @param speedLimiter 限速配置
     *                     应该为大于等于 0 的值，单位是 bytes，0 表示不限制
     */
    @Override
    public void setSpeedLimiter(@NotNull DownloaderSpeedLimiter speedLimiter) {
        long downloadLimit = speedLimiter.isDownloadUnlimited() ? 0 : speedLimiter.download();
        long uploadLimit = speedLimiter.isUploadUnlimited() ? 0 : speedLimiter.upload();
        var requestParam = Map.of(
                "up_limit", uploadLimit,
                "dl_limit", downloadLimit,
                "alt_up_limit", uploadLimit,
                "alt_dl_limit", downloadLimit,
                "limit_utp_rate", true,
                "limit_lan_peers", true,
                "scheduler_enabled", false
        );

        FormBody formBody = new FormBody.Builder()
                .add("json", JsonUtil.getGson().toJson(requestParam))
                .build();

        try {
            Request request = new Request.Builder()
                    .url(apiEndpoint + "/app/setPreferences")
                    .post(formBody)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error(tlUI(Lang.DOWNLOADER_QB_FAILED_SAVE_SPEED_LIMITER, getName(), apiEndpoint, response.code(), "HTTP ERROR", response.body() != null ? response.body().string() : "null"));
                    throw new IllegalStateException("Save qBittorrent shadow banlist error: statusCode=" + response.code());
                }
            }
        } catch (Exception e) {
            log.error(tlUI(Lang.DOWNLOADER_QB_FAILED_SAVE_SPEED_LIMITER, getName(), apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
            throw new IllegalStateException(e);
        }
    }

    private void addTracker(Torrent torrent, List<Tracker> newAdded) {
        StringJoiner joiner = new StringJoiner("\n");
        newAdded.forEach(t -> t.getTrackersInGroup().forEach(joiner::add));

        FormBody formBody = new FormBody.Builder()
                .add("hash", torrent.getId())
                .add("urls", joiner.toString())
                .build();

        try {
            Request request = new Request.Builder()
                    .url(apiEndpoint + "/torrents/addTrackers")
                    .post(formBody)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("Add qBittorrent tracker error: statusCode=" + response.code());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void removeTracker(Torrent torrent, List<Tracker> trackers) throws IllegalStateException {
        StringJoiner joiner = new StringJoiner("|");
        trackers.forEach(t -> t.getTrackersInGroup().forEach(joiner::add));

        FormBody formBody = new FormBody.Builder()
                .add("hash", torrent.getId())
                .add("urls", joiner.toString())
                .build();

        try {
            Request request = new Request.Builder()
                    .url(apiEndpoint + "/torrents/removeTrackers")
                    .post(formBody)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("Remove qBittorrent tracker error: statusCode=" + response.code());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected void fillTorrentProperties(List<QBittorrentTorrent> qbTorrent) {
        for (CompletableFuture<Void> future : qbTorrent.stream().map(detail -> CompletableFuture.runAsync(() -> {
            try {
                TorrentProperties properties = getTorrentProperties(detail);
                if (properties == null) {
                    log.warn("Failed to retrieve properties for torrent: {}", detail.getHash());
                    return;
                }
                if (detail.getCompleted() != properties.completed) {
                    // completed value changed, invalidate cache and fetch again.
                    torrentPropertiesCache.invalidate(detail.getHash());
                    properties = getTorrentProperties(detail);
                    if (properties == null) {
                        log.warn("Failed to retrieve properties after cache invalidation for torrent: {}", detail.getHash());
                        return;
                    }
                }
                if (config.isIgnorePrivate() && detail.getPrivateTorrent() == null) {
                    log.debug("Field is_private is not present, querying from properties API, hash: {}", detail.getHash());
                    detail.setPrivateTorrent(properties.isPrivate);
                }
                if (detail.getPieceSize() <= 0 || detail.getPiecesHave() <= 0) {
                    log.debug("Field piece_size or pieces_have is not present, querying from properties API, hash: {}", detail.getHash());
                    detail.setPieceSize(properties.pieceSize);
                    detail.setPiecesHave(properties.piecesHave);
                }
            } catch (Exception e) {
                log.debug("Failed to load properties cache", e);
            }
        }, parallelService)).toList()) {
            future.join();
        }
    }

    protected TorrentProperties getTorrentProperties(QBittorrentTorrent torrent) {
        try {
            return torrentPropertiesCache.get(torrent.getHash(), () -> {
                log.debug("torrent properties cache miss, query from properties api, hash: {}", torrent.getHash());

                Request request = new Request.Builder()
                        .url(apiEndpoint + "/torrents/properties?hash=" + torrent.getHash())
                        .get()
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        var newDetail = JsonUtil.getGson().fromJson(responseBody, QBittorrentTorrent.class);
                        return new TorrentProperties(newDetail.getPrivateTorrent(), torrent.getCompleted(), newDetail.getPieceSize(), newDetail.getPiecesHave());
                    }
                    // loader must not return null; it may either return a non-null value or throw an exception.
                    throw new IllegalStateException(String.format("Error fetching properties for torrent hash: %s, status: %d", torrent.getHash(), response.code()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public @NotNull DownloaderStatistics getStatistics() {
        try {
            Request request = new Request.Builder()
                    .url(apiEndpoint + "/sync/maindata")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IllegalStateException(tlUI(Lang.DOWNLOADER_FAILED_REQUEST_STATISTICS, response.code(), response.body() != null ? response.body().string() : "null"));
                }
                String responseBody = response.body().string();
                QBittorrentMainData mainData = JsonUtil.getGson().fromJson(responseBody, QBittorrentMainData.class);
                return new DownloaderStatistics(mainData.getServerState().getAlltimeUl(), mainData.getServerState().getAlltimeDl());
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public @NotNull List<Peer> getPeers(@NotNull Torrent torrent) {
        try {
            Request request = new Request.Builder()
                    .url(apiEndpoint + "/sync/torrentPeers?hash=" + torrent.getId())
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IllegalStateException(tlUI(Lang.DOWNLOADER_QB_FAILED_REQUEST_PEERS_LIST_IN_TORRENT, response.code(), response.body() != null ? response.body().string() : "null"));
                }
                String responseBody = response.body().string();
                JsonObject object = JsonParser.parseString(responseBody).getAsJsonObject();
                JsonObject peers = object.getAsJsonObject("peers");

                List<Peer> peersList = new ArrayList<>();
                for (String s : peers.keySet()) {
                    JsonObject singlePeerObject = peers.getAsJsonObject(s);
                    QBittorrentPeer qbPeer = JsonUtil.getGson().fromJson(singlePeerObject.toString(), QBittorrentPeer.class);
                    if ("HTTP".equalsIgnoreCase(qbPeer.getConnection()) || "HTTPS".equalsIgnoreCase(qbPeer.getConnection()) || "Web".equalsIgnoreCase(qbPeer.getConnection())) {
                        continue;
                    }
                    if (qbPeer.getPeerAddress().getIp() == null || qbPeer.getPeerAddress().getIp().isBlank()) {
                        continue;
                    }
                    if (s.contains(".onion") || s.contains(".i2p")) {
                        continue;
                    }
                    // 一个 QB 本地化问题的 Workaround
                    if (qbPeer.getPeerId() == null || "Unknown".equals(qbPeer.getPeerId()) || "未知".equals(qbPeer.getPeerId())) {
                        qbPeer.setPeerIdClient("");
                    }
                    if (qbPeer.getClientName() != null) {
                        if (qbPeer.getClientName().startsWith("Unknown [") && qbPeer.getClientName().endsWith("]")) {
                            String mid = qbPeer.getClientName().substring("Unknown [".length(), qbPeer.getClientName().length() - 1);
                            qbPeer.setClient(mid);
                        }
                    }
                    qbPeer.getPeerAddress().setRawIp(s);
                    qbPeer.setPeerAddress(natTranslate(qbPeer.getPeerAddress()));

                    peersList.add(qbPeer);
                }
                return peersList;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected void setBanListIncrement(Collection<BanMetadata> added) {
        Map<String, StringJoiner> banTasks = new HashMap<>();
        added.forEach(p -> {
            StringJoiner joiner = banTasks.getOrDefault(p.getTorrent().getHash(), new StringJoiner("|"));
            if (getFeatureFlags().contains(DownloaderFeatureFlag.RANGE_BAN_IP)) {
                joiner.add(remapBanListAddress(p.getPeer().getAddress().getAddress()).toNormalizedString());
            } else {
                joiner.add(p.getPeer().getRawIp());
            }
            banTasks.put(p.getTorrent().getHash(), joiner);
        });
        banTasks.forEach((hash, peers) -> {
            FormBody formBody = new FormBody.Builder()
                    .add("hash", hash)
                    .add("peers", peers.toString())
                    .build();

            try {
                Request request = new Request.Builder()
                        .url(apiEndpoint + "/transfer/banPeers")
                        .post(formBody)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        log.error(tlUI(Lang.DOWNLOADER_QB_INCREAMENT_BAN_FAILED, getName(), apiEndpoint, response.code(), "HTTP ERROR", response.body() != null ? response.body().string() : "null"));
                        throw new IllegalStateException("Save qBittorrent banlist error: statusCode=" + response.code());
                    }
                }
            } catch (Exception e) {
                log.error(tlUI(Lang.DOWNLOADER_QB_INCREAMENT_BAN_FAILED, getName(), apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
                throw new IllegalStateException(e);
            }
        });
    }

    protected void setBanListFull(Collection<IPAddress> bannedAddresses) {
        // todo change this with compatibility check after qbiitorrent merge it
        String banStr;
        if (getFeatureFlags().contains(DownloaderFeatureFlag.RANGE_BAN_IP)) {
            banStr = bannedAddresses.stream()
                    .map(ipAddr -> remapBanListAddress(ipAddr).toNormalizedString())
                    .distinct()
                    .collect(Collectors.joining("\n"));
        } else {
            StringJoiner joiner = new StringJoiner("\n");
            bannedAddresses.stream().distinct().forEach(ipAddr -> {
                joiner.add(ipAddr.toNormalizedString());
                if (ipAddr.isIPv4() && ipAddr.isIPv6Convertible()) {
                    inet.ipaddr.Address ipv6 = ipAddr.toIPv6();
                    if (ipv6 != null) {
                        joiner.add(ipv6.toNormalizedString());
                    }
                }
                if (ipAddr.isIPv6() && ipAddr.isIPv4Convertible()) {
                    Address ipv4 = ipAddr.toIPv4();
                    if (ipv4 != null) {
                        joiner.add(ipv4.toNormalizedString());
                    }
                }
            });
            banStr = joiner.toString();
        }

        FormBody formBody = new FormBody.Builder()
                .add("json", JsonUtil.getGson().toJson(Map.of("banned_IPs", banStr)))
                .build();

        try {
            Request request = new Request.Builder()
                    .url(apiEndpoint + "/app/setPreferences")
                    .post(formBody)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error(tlUI(Lang.DOWNLOADER_QB_FAILED_SAVE_BANLIST, getName(), apiEndpoint, response.code(), "HTTP ERROR", response.body() != null ? response.body().string() : "null"));
                    throw new IllegalStateException("Save qBittorrent banlist error: statusCode=" + response.code());
                }
            }
        } catch (Exception e) {
            log.error(tlUI(Lang.DOWNLOADER_QB_FAILED_SAVE_BANLIST, getName(), apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void setBTProtocolPort(int port) {
        var requestParam = Map.of(
                "listen_port", port
        );

        FormBody formBody = new FormBody.Builder()
                .add("json", JsonUtil.getGson().toJson(requestParam))
                .build();

        try {
            Request request = new Request.Builder()
                    .url(apiEndpoint + "/app/setPreferences")
                    .post(formBody)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error(tlUI(Lang.DOWNLOADER_FAILED_SAVE_BT_PROTOCOL_PORT, getName(), apiEndpoint, response.code(), "HTTP ERROR", response.body() != null ? response.body().string() : "null"));
                    throw new IllegalStateException("Save qBittorrent BTProtocolPort failed: statusCode=" + response.code());
                }
            }
        } catch (Exception e) {
            log.error(tlUI(Lang.DOWNLOADER_FAILED_SAVE_BT_PROTOCOL_PORT, getName(), apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int getBTProtocolPort() {
        try {
            Request request = new Request.Builder()
                    .url(apiEndpoint + "/app/preferences")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("Request failed with code: " + response.code());
                }
                String responseBody = response.body().string();
                QBittorrentPreferences preferences = JsonUtil.getGson()
                        .fromJson(responseBody, QBittorrentPreferences.class);
                return preferences.getListenPort();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @NotNull
    private String normalizeVersion(@NotNull String unprocessed) {
        unprocessed = unprocessed.trim();
        StringBuilder sb = new StringBuilder();
        StringBuilder suffix = new StringBuilder();
        boolean reachSuffix = false;
        for (char c : unprocessed.toCharArray()) {
            if (!reachSuffix && (Character.isDigit(c) || c == '.')) {
                sb.append(c);
            } else {
                reachSuffix = true;
                suffix.append(c);
            }
        }
        if (suffix.isEmpty()) {
            return sb.toString();
        }
        String suffixStr = suffix.toString();
        if (suffixStr.startsWith("-") || suffixStr.startsWith("+")) {
            return sb + suffixStr;
        }
        return sb + "-" + suffixStr;
    }

    @Override
    public void close() {
        if (!parallelService.isShutdown()) {
            parallelService.shutdownNow();
        }
    }

    public record TorrentProperties(boolean isPrivate, long completed, long pieceSize, long piecesHave) {
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class QBittorrentBuildInfo {
        @JsonProperty("bitness")
        private Integer bitness;
        @JsonProperty("boost")
        private String boost;
        @JsonProperty("libtorrent")
        private String libtorrent;
        @JsonProperty("openssl")
        private String openssl;
        @JsonProperty("platform")
        private String platform;
        @JsonProperty("qt")
        private String qt;
        @JsonProperty("zlib")
        private String zlib;
    }
}
