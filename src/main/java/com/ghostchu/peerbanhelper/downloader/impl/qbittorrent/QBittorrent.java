package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent;

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

import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class QBittorrent extends AbstractDownloader {
    private final String apiEndpoint;
    private final HttpClient httpClient;
    private final Config config;

    private final Map<String, Boolean> isPrivateCacheMap = new LinkedHashMap<>(2000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > 2000;
        }
    };
    private final ExecutorService isPrivateExecutorService = Executors.newFixedThreadPool(10); // Controls the number of concurrent API requests
    private final Semaphore isPrivateSemaphore = new Semaphore(5); // Limits the concurrent access to 5

    public QBittorrent(String name, Config config) {
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
    }

    public static QBittorrent loadFromConfig(String name, JsonObject section) {
        Config config = JsonUtil.getGson().fromJson(section.toString(), Config.class);
        return new QBittorrent(name, config);
    }

    public static QBittorrent loadFromConfig(String name, ConfigurationSection section) {
        Config config = Config.readFromYaml(section);
        return new QBittorrent(name, config);
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
                return new DownloaderLoginResult(DownloaderLoginResult.Status.SUCCESS, new TranslationComponent(Lang.STATUS_TEXT_OK));
            }
            return new DownloaderLoginResult(DownloaderLoginResult.Status.INCORRECT_CREDENTIAL, new TranslationComponent(Lang.DOWNLOADER_LOGIN_EXCEPTION, request.body()));
            // return request.statusCode() == 200;
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
        return "qBittorrent";
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
            setBanListIncrement(added);
        } else {
            setBanListFull(fullList);
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
        List<Torrent> torrents = new ArrayList<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (QBTorrent detail : qbTorrent) {
            if (config.isIgnorePrivate()) {
                if (detail.getPrivateTorrent() == null) {
                    futures.add(CompletableFuture.runAsync(() -> {
                        try {
                            isPrivateSemaphore.acquire();
                            checkAndSetPrivateField(detail);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            isPrivateSemaphore.release();
                        }
                    }, isPrivateExecutorService));
                } else if (detail.getPrivateTorrent()) {
                    continue;
                }
            }

            torrents.add(new TorrentImpl(detail.getHash(), detail.getName(), detail.getHash(), detail.getTotalSize(),
                    detail.getProgress(), detail.getUpspeed(), detail.getDlspeed(),
                    detail.getPrivateTorrent() != null && detail.getPrivateTorrent()));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return torrents;
    }

    private void checkAndSetPrivateField(QBTorrent detail) {
        String hash = detail.getHash();
        if (isPrivateCacheMap.containsKey(hash)) {
            Boolean isPrivate = isPrivateCacheMap.get(hash);
            detail.setPrivateTorrent(isPrivate);
            return;
        }

        try {
            HttpResponse<String> res = httpClient.send(
                    MutableRequest.GET(apiEndpoint + "/torrents/properties?hash=" + hash),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (res.statusCode() == 200) {
                QBTorrent newDetail = JsonUtil.getGson().fromJson(res.body(), QBTorrent.class);
                Boolean isPrivate = newDetail.getPrivateTorrent();
                synchronized (isPrivateCacheMap) {
                    isPrivateCacheMap.put(hash, isPrivate);
                }
                detail.setPrivateTorrent(isPrivate);
            } else {
                log.warn("Error fetching properties for torrent hash: {}, status: {}", hash, res.statusCode());
            }
        } catch (Exception e) {
            log.error("Error fetching properties for torrent hash: {}", hash, e);
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
            QBPeer qbPeer = JsonUtil.getGson().fromJson(singlePeerObject.toString(), QBPeer.class);
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

    private void setBanListIncrement(Collection<BanMetadata> added) {
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

    private void setBanListFull(Collection<PeerAddress> peerAddresses) {
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

    @Override
    public void close() throws Exception {

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
        private boolean useShadowBan;
        private boolean verifySsl;
        private boolean ignorePrivate;

        public static Config readFromYaml(ConfigurationSection section) {
            Config config = new Config();
            config.setType("qbittorrent");
            config.setEndpoint(section.getString("endpoint"));
            if (config.getEndpoint().endsWith("/")) { // 浏览器复制党 workaround 一下， 避免连不上的情况
                config.setEndpoint(config.getEndpoint().substring(0, config.getEndpoint().length() - 1));
            }
            config.setUsername(section.getString("username", ""));
            config.setPassword(section.getString("password", ""));
            Config.BasicauthDTO basicauthDTO = new BasicauthDTO();
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
            section.set("type", "qbittorrent");
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
