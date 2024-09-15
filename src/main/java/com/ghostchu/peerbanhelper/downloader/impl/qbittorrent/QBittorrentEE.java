package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.AbstractDownloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLoginResult;
import com.ghostchu.peerbanhelper.downloader.DownloaderStatistics;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.torrent.TorrentImpl;
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
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class QBittorrentEE extends AbstractDownloader {
    private final String apiEndpoint;
    private final HttpClient httpClient;
    private final Config config;
    private final BanHandler banHandler;

    private final Cache<String, Boolean> isPrivateCache;
    private final ExecutorService isPrivateExecutorService = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore isPrivateSemaphore = new Semaphore(5);

    public QBittorrentEE(String name, Config config) {
        super(name);
        this.config = config;
        this.apiEndpoint = config.getEndpoint() + "/api/v2";
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        Methanol.Builder builder = Methanol
                .newBuilder()
                .version(HttpClient.Version.valueOf(config.getHttpVersion()))
                .defaultHeader("Accept-Encoding", "gzip,deflate")
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .headersTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .readTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .requestTimeout(Duration.of(30, ChronoUnit.SECONDS))
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
        if (config.isUseShadowBan()) {
            this.banHandler = new BanHandlerShadowBan(httpClient, name, apiEndpoint);
        } else {
            this.banHandler = new BanHandlerNormal(httpClient, name, apiEndpoint);
        }

        YamlConfiguration profileConfig = Main.getProfileConfig();
        this.isPrivateCache = CacheBuilder.newBuilder()
            .maximumSize(2000)
            .expireAfterAccess(
                profileConfig.getLong("check-interval", 5000) + (1000 * 60),
                TimeUnit.MILLISECONDS
            )
            .build();
    }

    public static QBittorrentEE loadFromConfig(String name, JsonObject section) {
        Config config = JsonUtil.getGson().fromJson(section.toString(), Config.class);
        return new QBittorrentEE(name, config);
    }

    public static QBittorrentEE loadFromConfig(String name, ConfigurationSection section) {
        Config config = Config.readFromYaml(section);
        return new QBittorrentEE(name, config);
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
        if (isLoggedIn()) {
            try {
                if (config.isUseShadowBan() && !banHandler.test()) {
                    return new DownloaderLoginResult(DownloaderLoginResult.Status.REQUIRE_TAKE_ACTIONS, new TranslationComponent(Lang.DOWNLOADER_QBITTORRENTEE_SHADOWBANAPI_TEST_FAILURE));
                }
            } catch (Exception e) {
                return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_LOGIN_IO_EXCEPTION, e.getClass().getName() + ": " + e.getMessage()));
            }
            return new DownloaderLoginResult(DownloaderLoginResult.Status.SUCCESS, new TranslationComponent(Lang.STATUS_TEXT_OK)); // 重用 Session 会话
        }
        try {
            HttpResponse<String> request = httpClient
                    .send(MutableRequest.POST(apiEndpoint + "/auth/login",
                                            FormBodyPublisher.newBuilder()
                                                    .query("username", config.getUsername())
                                                    .query("password", config.getPassword()).build())
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                            , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (request.statusCode() == 200 && isLoggedIn()) {
                try {
                    if (config.isUseShadowBan() && !banHandler.test()) {
                        return new DownloaderLoginResult(DownloaderLoginResult.Status.REQUIRE_TAKE_ACTIONS, new TranslationComponent(Lang.DOWNLOADER_QBITTORRENTEE_SHADOWBANAPI_TEST_FAILURE));
                    }
                } catch (Exception e) {
                    return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_LOGIN_IO_EXCEPTION, e.getClass().getName() + ": " + e.getMessage()));
                }
                return new DownloaderLoginResult(DownloaderLoginResult.Status.SUCCESS, new TranslationComponent(Lang.STATUS_TEXT_OK));
            }
            return new DownloaderLoginResult(DownloaderLoginResult.Status.INCORRECT_CREDENTIAL, new TranslationComponent(Lang.DOWNLOADER_LOGIN_EXCEPTION, request.body()));
        } catch (Exception e) {
            return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_LOGIN_IO_EXCEPTION, e.getClass().getName() + ": " + e.getMessage()));
        }
    }

    @Override
    public String getEndpoint() {
        return apiEndpoint;
    }


    @Override
    public String getType() {
        return "qBittorrentEE";
    }

    public boolean isLoggedIn() {
        HttpResponse<Void> resp;
        try {
            resp = httpClient.send(MutableRequest.GET(apiEndpoint + "/app/version"), HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            return false;
        }
        return resp.statusCode() == 200;
    }

    @Override
    public void setBanList(@NotNull Collection<PeerAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed, boolean applyFullList) {
        if (removed != null && removed.isEmpty() && added != null && config.isIncrementBan() && !applyFullList) {
            banHandler.setBanListIncrement(added);
        } else {
            banHandler.setBanListFull(fullList);
        }
    }

    @Override
    public List<Torrent> getTorrents() {
        HttpResponse<String> request;
        try {
            request = httpClient.send(MutableRequest.GET(apiEndpoint + "/torrents/info?filter=active"), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (request.statusCode() != 200) {
            throw new IllegalStateException(tlUI(Lang.DOWNLOADER_QB_FAILED_REQUEST_TORRENT_LIST, request.statusCode(), request.body()));
        }
        List<QBTorrent> qbTorrent = JsonUtil.getGson().fromJson(request.body(), new TypeToken<List<QBTorrent>>() {
        }.getType());
        List<CompletableFuture<QBTorrent>> futures = new ArrayList<>();

        for (QBTorrent detail : qbTorrent) {
            if (config.isIgnorePrivate() && detail.getPrivateTorrent() == null) {
                CompletableFuture<QBTorrent> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        isPrivateSemaphore.acquire();
                        String hash = detail.getHash();
                        detail.setPrivateTorrent(isPrivateCache.get(hash, () -> fetchPrivateStatus(hash)));
                    } catch (Exception e) {
                        log.debug("Failed to load private cache", e);
                    } finally {
                        isPrivateSemaphore.release();
                    }
                    return detail;
                }, isPrivateExecutorService);
                futures.add(future);
            } else {
                futures.add(CompletableFuture.completedFuture(detail));
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        List<Torrent> torrents = futures.stream()
            .map(future -> {
                try {
                    return future.get();
                } catch (Exception e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .filter(detail -> !(config.isIgnorePrivate() && detail.getPrivateTorrent() != null && detail.getPrivateTorrent()))
            .map(detail -> new TorrentImpl(detail.getHash(), detail.getName(), detail.getHash(), detail.getTotalSize(),
                    detail.getProgress(), detail.getUpspeed(), detail.getDlspeed(),
                    detail.getPrivateTorrent() != null && detail.getPrivateTorrent()))
            .collect(Collectors.toList());

        return torrents;
    }

    private Boolean fetchPrivateStatus(String hash) {
        try {
            log.debug("Field is_private is not present and cache miss, query from properties api, hash: {}", hash);
            HttpResponse<String> res = httpClient.send(
                    MutableRequest.GET(apiEndpoint + "/torrents/properties?hash=" + hash),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (res.statusCode() == 200) {
                QBTorrent newDetail = JsonUtil.getGson().fromJson(res.body(), QBTorrent.class);
                Boolean isPrivate = newDetail.getPrivateTorrent();
                return isPrivate;
            } else {
                log.warn("Error fetching properties for torrent hash: {}, status: {}", hash, res.statusCode());
            }
        } catch (Exception e) {
            log.warn("Error fetching properties for torrent hash: {}", hash, e);
        }
        return null;
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
        QBMainData mainData = JsonUtil.getGson().fromJson(request.body(), QBMainData.class);
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
            QBEEPeer qbPeer = JsonUtil.getGson().fromJson(singlePeerObject.toString(), QBEEPeer.class);
            if (qbPeer.getShadowBanned()) {
                continue; // 当做不存在处理
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


    @Override
    public void close() throws Exception {
        if (!isPrivateExecutorService.isShutdown()) {
            isPrivateExecutorService.shutdownNow();
        }
    }

    interface BanHandler {
        boolean test();

        void setBanListIncrement(Collection<BanMetadata> added);

        void setBanListFull(Collection<PeerAddress> peerAddresses);
    }

    public static class BanHandlerNormal implements BanHandler {

        private final HttpClient httpClient;
        private final String name;
        private final String apiEndpoint;

        public BanHandlerNormal(HttpClient httpClient, String name, String apiEndpoint) {
            this.httpClient = httpClient;
            this.name = name;
            this.apiEndpoint = apiEndpoint;
        }

        @Override
        public boolean test() {
            return true;
        }

        @Override
        public void setBanListIncrement(Collection<BanMetadata> added) {
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

        @Override
        public void setBanListFull(Collection<PeerAddress> peerAddresses) {
            StringJoiner joiner = new StringJoiner("\n");
            peerAddresses.forEach(p -> joiner.add(p.getIp()));
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
    }

    public static class BanHandlerShadowBan implements BanHandler {

        private final HttpClient httpClient;
        private final String name;
        private final String apiEndpoint;
        private Boolean shadowBanEnabled = false; // 缓存 shadowBan 开关状态

        public BanHandlerShadowBan(HttpClient httpClient, String name, String apiEndpoint) {
            this.httpClient = httpClient;
            this.name = name;
            this.apiEndpoint = apiEndpoint;
        }

        @Override
        public boolean test() {
            if (shadowBanEnabled)
                return true;
            try {
                HttpResponse<String> request = httpClient.send(MutableRequest.GET(apiEndpoint + "/app/preferences")
                        , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                Preferences preferences = JsonUtil.getGson().fromJson(request.body(), Preferences.class);
                shadowBanEnabled = preferences.getShadowBanEnabled() != null && preferences.getShadowBanEnabled();
                return shadowBanEnabled;
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void setBanListIncrement(Collection<BanMetadata> added) {
            Map<String, StringJoiner> banTasks = new HashMap<>();
            added.forEach(p -> {
                StringJoiner joiner = banTasks.getOrDefault(p.getTorrent().getHash(), new StringJoiner("|"));
                joiner.add(p.getPeer().getRawIp());
                banTasks.put(p.getTorrent().getHash(), joiner);
            });
            banTasks.forEach((hash, peers) -> {
                try {
                    HttpResponse<String> request = httpClient.send(MutableRequest
                                    .POST(apiEndpoint + "/transfer/shadowbanPeers", FormBodyPublisher.newBuilder()
                                            .query("hash", hash)
                                            .query("peers", peers.toString()).build())
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                            , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (request.statusCode() != 200) {
                        log.error(tlUI(Lang.DOWNLOADER_QB_INCREAMENT_BAN_FAILED, name, apiEndpoint, request.statusCode(), "HTTP ERROR", request.body()));
                        throw new IllegalStateException("Save qBittorrent shadow banlist error: statusCode=" + request.statusCode());
                    }
                } catch (Exception e) {
                    log.error(tlUI(Lang.DOWNLOADER_QB_INCREAMENT_BAN_FAILED, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
                    throw new IllegalStateException(e);
                }
            });
        }

        @Override
        public void setBanListFull(Collection<PeerAddress> peerAddresses) {
            StringJoiner joiner = new StringJoiner("\n");
            peerAddresses.forEach(p -> joiner.add(p.getIp()));
            try {
                HttpResponse<String> request = httpClient.send(MutableRequest
                                .POST(apiEndpoint + "/app/setPreferences", FormBodyPublisher.newBuilder()
                                        .query("json", JsonUtil.getGson().toJson(Map.of("shadow_banned_IPs", joiner.toString()))).build())
                                .header("Content-Type", "application/x-www-form-urlencoded")
                        , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (request.statusCode() != 200) {
                    log.error(tlUI(Lang.DOWNLOADER_QB_FAILED_SAVE_BANLIST, name, apiEndpoint, request.statusCode(), "HTTP ERROR", request.body()));
                    throw new IllegalStateException("Save qBittorrent shadow banlist error: statusCode=" + request.statusCode());
                }
            } catch (Exception e) {
                log.error(tlUI(Lang.DOWNLOADER_QB_FAILED_SAVE_BANLIST, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
                throw new IllegalStateException(e);
            }
        }
    }


    @NoArgsConstructor
    @Data
    public static class Config {

        private String type;
        private String endpoint;
        private String username;
        private String password;
        private BasicauthDTO basicAuth;
        private String httpVersion;
        private boolean incrementBan;
        private boolean verifySsl;
        private boolean useShadowBan;
        private boolean ignorePrivate;

        public static Config readFromYaml(ConfigurationSection section) {
            Config config = new Config();
            config.setType("qbittorrentee");
            config.setEndpoint(section.getString("endpoint"));
            if (config.getEndpoint().endsWith("/")) { // 浏览器复制党 workaround 一下， 避免连不上的情况
                config.setEndpoint(config.getEndpoint().substring(0, config.getEndpoint().length() - 1));
            }
            config.setUsername(section.getString("username", ""));
            config.setPassword(section.getString("password", ""));
            BasicauthDTO basicauthDTO = new BasicauthDTO();
            basicauthDTO.setUser(section.getString("basic-auth.user"));
            basicauthDTO.setPass(section.getString("basic-auth.pass"));
            config.setBasicAuth(basicauthDTO);
            config.setHttpVersion(section.getString("http-version", "HTTP_1_1"));
            config.setIncrementBan(section.getBoolean("increment-ban", false));
            config.setUseShadowBan(section.getBoolean("use-shadow-ban", false));
            config.setVerifySsl(section.getBoolean("verify-ssl", true));
            config.setIgnorePrivate(section.getBoolean("ignore-private", false));
            return config;
        }

        public YamlConfiguration saveToYaml() {
            YamlConfiguration section = new YamlConfiguration();
            section.set("type", "qbittorrentee");
            section.set("endpoint", endpoint);
            section.set("username", username);
            section.set("password", password);
            section.set("basic-auth.user", Objects.requireNonNullElse(basicAuth.user, ""));
            section.set("basic-auth.pass", Objects.requireNonNullElse(basicAuth.pass, ""));
            section.set("http-version", httpVersion);
            section.set("increment-ban", incrementBan);
            section.set("use-shadow-ban", useShadowBan);
            section.set("verify-ssl", verifySsl);
            section.set("ignore-private", ignorePrivate);
            return section;
        }

        @NoArgsConstructor
        @Data
        public static class BasicauthDTO {
            @SerializedName("user")
            private String user;
            @SerializedName("pass")
            private String pass;
        }
    }
}
