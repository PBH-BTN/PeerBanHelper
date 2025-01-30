package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.downloader.AbstractDownloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLoginResult;
import com.ghostchu.peerbanhelper.downloader.DownloaderStatistics;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.QBittorrentMainData;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.QBittorrentPeer;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.QBittorrentTorrent;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.QBittorrentTorrentTrackers;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.torrent.Tracker;
import com.ghostchu.peerbanhelper.torrent.TrackerImpl;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.github.mizosoft.methanol.FormBodyPublisher;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public abstract class AbstractQbittorrent extends AbstractDownloader {
    protected final String apiEndpoint;
    protected final HttpClient httpClient;
    protected final QBittorrentConfig config;
    protected final Cache<String, TorrentProperties> torrentPropertiesCache;

    public AbstractQbittorrent(String name, QBittorrentConfig config, AlertManager alertManager) {
        super(name, alertManager);
        this.config = config;
        this.apiEndpoint = config.getEndpoint() + "/api/v2";
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        Methanol.Builder builder = Methanol
                .newBuilder()
                .version(HttpClient.Version.valueOf(config.getHttpVersion()))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .headersTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .readTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .authenticator(new Authenticator() {
                    @Override
                    public PasswordAuthentication requestPasswordAuthenticationInstance(String host, InetAddress addr, int port, String protocol, String prompt, String scheme, URL url, RequestorType reqType) {
                        return new PasswordAuthentication(config.getBasicAuth().getUser(), config.getBasicAuth().getPass().toCharArray());
                    }
                })
                .cookieHandler(cm);
        if (!config.isVerifySsl() && HTTPUtil.getIgnoreSslContext() != null) {
            builder.sslContext(HTTPUtil.getIgnoreSslContext());
        }
        this.httpClient = builder.build();

        YamlConfiguration profileConfig = Main.getProfileConfig();
        this.torrentPropertiesCache = CacheBuilder.newBuilder()
                .maximumSize(2000)
                .expireAfterAccess(
                        profileConfig.getLong("check-interval", 5000) + (1000 * 60),
                        TimeUnit.MILLISECONDS
                )
                .build();
    }

    @Override
    public JsonObject saveDownloaderJson() {
        return JsonUtil.getGson().toJsonTree(config).getAsJsonObject();
    }

    @Override
    public YamlConfiguration saveDownloader() {
        return config.saveToYaml();
    }

    public DownloaderLoginResult login0() {
        if (isLoggedIn())
            return new DownloaderLoginResult(DownloaderLoginResult.Status.SUCCESS, new TranslationComponent(Lang.STATUS_TEXT_OK)); // 重用 Session 会话
        try {
            HttpResponse<String> request = httpClient
                    .send(MutableRequest.POST(apiEndpoint + "/auth/login",
                                            FormBodyPublisher.newBuilder()
                                                    .query("username", config.getUsername())
                                                    .query("password", config.getPassword()).build())
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                            , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (request.statusCode() == 200 && isLoggedIn()) {
                updatePreferences();
                return new DownloaderLoginResult(DownloaderLoginResult.Status.SUCCESS, new TranslationComponent(Lang.STATUS_TEXT_OK));
            }
            return new DownloaderLoginResult(DownloaderLoginResult.Status.INCORRECT_CREDENTIAL, new TranslationComponent(Lang.DOWNLOADER_LOGIN_EXCEPTION, request.body()));
            // return request.statusCode() == 200;
        } catch (Exception e) {
            return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_LOGIN_IO_EXCEPTION, e.getClass().getName() + ": " + e.getMessage()));
        }
    }

    public void updatePreferences() {
        try {
            HttpResponse<String> request = httpClient.send(MutableRequest
                            .POST(apiEndpoint + "/app/setPreferences", FormBodyPublisher.newBuilder()
                                    .query("json", JsonUtil.getGson().toJson(Map.of("enable_multi_connections_from_same_ip", false))).build())
                            .header("Content-Type", "application/x-www-form-urlencoded")
                    , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (request.statusCode() != 200) {
                log.error(tlUI(Lang.DOWNLOADER_QB_DISABLE_SAME_IP_MULTI_CONNECTION_FAILED, name, apiEndpoint, request.statusCode(), "HTTP ERROR", request.body()));
                throw new IllegalStateException("Save qBittorrent preferences error: statusCode=" + request.statusCode());
            }
        } catch (Exception e) {
            log.error(tlUI(Lang.DOWNLOADER_QB_DISABLE_SAME_IP_MULTI_CONNECTION_FAILED, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
            throw new IllegalStateException(e);
        }
    }


    @Override
    public String getEndpoint() {
        return apiEndpoint;
    }


    public boolean isLoggedIn() {
        HttpResponse<String> resp;
        try {
            resp = httpClient.send(MutableRequest.GET(apiEndpoint + "/app/buildInfo"), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return false;
            }
            QBittorrentBuildInfo info = JsonUtil.getGson().fromJson(resp.body(), QBittorrentBuildInfo.class);
            if (info == null) {
                return false;
            }
            if (info.getLibtorrent() == null) {
                return false;
            }
            return !info.getLibtorrent().isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void setBanList(@NotNull Collection<PeerAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed, boolean applyFullList) {
        if (removed != null && removed.isEmpty() && added != null && config.isIncrementBan() && !applyFullList) {
            setBanListIncrement(added);
        } else {
            setBanListFull(fullList);
        }
    }

    @Override
    public List<Torrent> getTorrents() {
        return fetchTorrents(true, !config.isIgnorePrivate());
    }

    @Override
    public List<Torrent> getAllTorrents() {
        return fetchTorrents(false, true);
    }

    private List<Torrent> fetchTorrents(boolean onlyActive, boolean includePrivate) {
        HttpResponse<String> request;
        try {
            String url = apiEndpoint + "/torrents/info";
            if (onlyActive) {
                url += "?filter=active";
            }
            request = httpClient.send(MutableRequest.GET(url), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (request.statusCode() != 200) {
            throw new IllegalStateException(tlUI(Lang.DOWNLOADER_QB_FAILED_REQUEST_TORRENT_LIST, request.statusCode(), request.body()));
        }
        List<QBittorrentTorrent> qbTorrent = JsonUtil.getGson().fromJson(request.body(), new TypeToken<List<QBittorrentTorrent>>() {
        }.getType());

        fillTorrentProperties(qbTorrent);

        return qbTorrent.stream().map(t -> (Torrent) t)
                .filter(t -> includePrivate || !t.isPrivate())
                .collect(Collectors.toList());
    }

    @Override
    public List<Tracker> getTrackers(Torrent torrent) {
        HttpResponse<String> request;
        try {
            request = httpClient.send(MutableRequest.GET(apiEndpoint + "/torrents/trackers?hash=" + torrent.getId()), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (request.statusCode() != 200) {
            throw new IllegalStateException(tlUI(Lang.DOWNLOADER_FAILED_REQUEST_TRACKER_LIST_ON_TORRENT, torrent.getHash(), request.statusCode(), request.body()));
        }
        List<QBittorrentTorrentTrackers> qbTorrentTrackers = JsonUtil.getGson().fromJson(request.body(), new TypeToken<List<QBittorrentTorrentTrackers>>() {
        }.getType());
        qbTorrentTrackers = qbTorrentTrackers.stream()
                .filter(t -> !t.getUrl().startsWith("**"))
                .sorted(Comparator.comparingInt(QBittorrentTorrentTrackers::getTier)).toList();
        Map<Integer, List<String>> trackerMap = new HashMap<>();
        for (QBittorrentTorrentTrackers qbTorrentTracker : qbTorrentTrackers) {
            trackerMap.computeIfAbsent(qbTorrentTracker.getTier(), k -> new ArrayList<>()).add(qbTorrentTracker.getUrl());
        }
        List<Tracker> trackers = new ArrayList<>();
        trackerMap.forEach((k, v) -> {
            trackers.add(new TrackerImpl(v));
        });
        return trackers;
    }

    @Override
    public void setTrackers(Torrent torrent, List<Tracker> trackers) {
        List<Tracker> trackerList = getTrackers(torrent);
        removeTracker(torrent, trackerList);
        addTracker(torrent, trackers);
    }

    private void addTracker(Torrent torrent, List<Tracker> newAdded) {
        StringJoiner joiner = new StringJoiner("\n");
        newAdded.forEach(t -> t.getTrackersInGroup().forEach(joiner::add));
        try {
            HttpResponse<String> request = httpClient.send(MutableRequest
                            .POST(apiEndpoint + "/torrents/addTrackers", FormBodyPublisher.newBuilder()
                                    .query("hash", torrent.getId())
                                    .query("urls", joiner.toString()).build())
                            .header("Content-Type", "application/x-www-form-urlencoded")
                    , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (request.statusCode() != 200) {
                throw new IllegalStateException("Add qBittorrent tracker error: statusCode=" + request.statusCode());
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void removeTracker(Torrent torrent, List<Tracker> trackers) throws IllegalStateException {
        StringJoiner joiner = new StringJoiner("|");
        trackers.forEach(t -> t.getTrackersInGroup().forEach(joiner::add));
        try {
            HttpResponse<String> request = httpClient.send(MutableRequest
                            .POST(apiEndpoint + "/torrents/removeTrackers", FormBodyPublisher.newBuilder()
                                    .query("hash", torrent.getId())
                                    .query("urls", joiner.toString()).build())
                            .header("Content-Type", "application/x-www-form-urlencoded")
                    , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (request.statusCode() != 200) {
                throw new IllegalStateException("Remove qBittorrent tracker error: statusCode=" + request.statusCode());
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected void fillTorrentProperties(List<QBittorrentTorrent> qbTorrent) {
        Semaphore torrentPropertiesLimit = new Semaphore(5);
        try (ExecutorService service = Executors.newVirtualThreadPerTaskExecutor()) {
            qbTorrent.stream()
                    .filter(torrent -> (config.isIgnorePrivate() && torrent.getPrivateTorrent() == null)
                            || torrent.getPieceSize() <= 0 || torrent.getPiecesHave() <= 0)
                    .forEach(detail -> service.submit(() -> {
                        try {
                            torrentPropertiesLimit.acquire();
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
                        } finally {
                            torrentPropertiesLimit.release();
                        }
                    }));
        }
    }

    protected TorrentProperties getTorrentProperties(QBittorrentTorrent torrent) {
        try {
            return torrentPropertiesCache.get(torrent.getHash(), () -> {
                log.debug("torrent properties cache miss, query from properties api, hash: {}", torrent.getHash());
                HttpResponse<String> res = httpClient.send(
                        MutableRequest.GET(apiEndpoint + "/torrents/properties?hash=" + torrent.getHash()),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );
                if (res.statusCode() == 200) {
                    var newDetail = JsonUtil.getGson().fromJson(res.body(), QBittorrentTorrent.class);
                    return new TorrentProperties(newDetail.getPrivateTorrent(), torrent.getCompleted(), newDetail.getPieceSize(), newDetail.getPiecesHave());
                }
                // loader must not return null; it may either return a non-null value or throw an exception.
                throw new IllegalStateException(String.format("Error fetching properties for torrent hash: %s, status: %d", torrent.getHash(), res.statusCode()));
            });
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public DownloaderStatistics getStatistics() {
        HttpResponse<String> request;
        try {
            request = httpClient.send(MutableRequest.GET(apiEndpoint + "/sync/maindata"), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (request.statusCode() != 200) {
            throw new IllegalStateException(tlUI(Lang.DOWNLOADER_FAILED_REQUEST_STATISTICS, request.statusCode(), request.body()));
        }
        QBittorrentMainData mainData = JsonUtil.getGson().fromJson(request.body(), QBittorrentMainData.class);
        return new DownloaderStatistics(mainData.getServerState().getAlltimeUl(), mainData.getServerState().getAlltimeDl());
    }

    @Override
    public List<Peer> getPeers(Torrent torrent) {
        HttpResponse<String> resp;
        try {
            resp = httpClient.send(MutableRequest.GET(apiEndpoint + "/sync/torrentPeers?hash=" + torrent.getId()),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (resp.statusCode() != 200) {
            throw new IllegalStateException(tlUI(Lang.DOWNLOADER_QB_FAILED_REQUEST_PEERS_LIST_IN_TORRENT, resp.statusCode(), resp.body()));
        }

        JsonObject object = JsonParser.parseString(resp.body()).getAsJsonObject();
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
            if (qbPeer.getRawIp().contains(".onion") || qbPeer.getRawIp().contains(".i2p")) {
                continue;
            }
            // 一个 QB 本地化问题的 Workaround
            if (qbPeer.getPeerId() == null || qbPeer.getPeerId().equals("Unknown") || qbPeer.getPeerId().equals("未知")) {
                qbPeer.setPeerIdClient("");
            }
            if (qbPeer.getClientName() != null) {
                if (qbPeer.getClientName().startsWith("Unknown [") && qbPeer.getClientName().endsWith("]")) {
                    String mid = qbPeer.getClientName().substring("Unknown [".length(), qbPeer.getClientName().length() - 1);
                    qbPeer.setClient(mid);
                }
            }
            qbPeer.setRawIp(s);
            peersList.add(qbPeer);
        }
        return peersList;
    }

    protected void setBanListIncrement(Collection<BanMetadata> added) {
        Map<String, StringJoiner> banTasks = new HashMap<>();
        added.forEach(p -> {
            StringJoiner joiner = banTasks.getOrDefault(p.getTorrent().getHash(), new StringJoiner("|"));
            joiner.add(p.getPeer().getRawIp());
            banTasks.put(p.getTorrent().getHash(), joiner);
        });
        banTasks.forEach((hash, peers) -> {
            try {
                HttpResponse<String> request = httpClient.send(MutableRequest
                                .POST(apiEndpoint + "/transfer/banPeers", FormBodyPublisher.newBuilder()
                                        .query("hash", hash)
                                        .query("peers", peers.toString()).build())
                                .header("Content-Type", "application/x-www-form-urlencoded")
                        , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (request.statusCode() != 200) {
                    log.error(tlUI(Lang.DOWNLOADER_QB_INCREAMENT_BAN_FAILED, name, apiEndpoint, request.statusCode(), "HTTP ERROR", request.body()));
                    throw new IllegalStateException("Save qBittorrent banlist error: statusCode=" + request.statusCode());
                }
            } catch (Exception e) {
                log.error(tlUI(Lang.DOWNLOADER_QB_INCREAMENT_BAN_FAILED, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
                throw new IllegalStateException(e);
            }
        });
    }

    protected void setBanListFull(Collection<PeerAddress> peerAddresses) {
        StringJoiner joiner = new StringJoiner("\n");
        peerAddresses.stream().map(PeerAddress::getIp).distinct().forEach(joiner::add);
        try {
            HttpResponse<String> request = httpClient.send(MutableRequest
                            .POST(apiEndpoint + "/app/setPreferences", FormBodyPublisher.newBuilder()
                                    .query("json", JsonUtil.getGson().toJson(Map.of("banned_IPs", joiner.toString()))).build())
                            .header("Content-Type", "application/x-www-form-urlencoded")
                    , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (request.statusCode() != 200) {
                log.error(tlUI(Lang.DOWNLOADER_QB_FAILED_SAVE_BANLIST, name, apiEndpoint, request.statusCode(), "HTTP ERROR", request.body()));
                throw new IllegalStateException("Save qBittorrent banlist error: statusCode=" + request.statusCode());
            }
        } catch (Exception e) {
            log.error(tlUI(Lang.DOWNLOADER_QB_FAILED_SAVE_BANLIST, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
            throw new IllegalStateException(e);
        }
    }


    @Override
    public void close() throws Exception {

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
