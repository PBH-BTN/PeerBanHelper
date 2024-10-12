package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.AbstractDownloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLoginResult;
import com.ghostchu.peerbanhelper.downloader.DownloaderStatistics;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.QBittorrentMainData;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.QBittorrentPeer;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.QBittorrentTorrent;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.FormBody;
import okhttp3.Interceptor;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.*;
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
    protected final OkHttpClient httpClient;
    protected final QBittorrentConfig config;
    protected final Cache<String, Boolean> isPrivateCache;

    public AbstractQbittorrent(String name, QBittorrentConfig config) {
        super(name);
        this.config = config;
        this.apiEndpoint = config.getEndpoint() + "/api/v2";
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        OkHttpClient.Builder builder = Main.getSharedHttpClient()
                .newBuilder()
                .followRedirects(true)
                .connectTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .readTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .cookieJar(new JavaNetCookieJar(cm))
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Interceptor.Chain chain) throws IOException {
                        Request original = chain.request();
                        Request request = original.newBuilder()
                                .header("Accept", "application/json")
                                //.header("Accept-Encoding", "gzip,deflate")
                                .header("Content-Type", "application/json")
                                .header("Authorization", Credentials.basic(config.getBasicAuth().getUser(), config.getBasicAuth().getPass()))
                                .method(original.method(), original.body())
                                .build();
                        return chain.proceed(request);
                    }
                });
        if (!config.isVerifySsl() && HTTPUtil.getIgnoreSSLSocketFactory() != null) {
            builder.sslSocketFactory(HTTPUtil.getIgnoreSSLSocketFactory(), HTTPUtil.getIgnoreTrustManager());
        }
        if (config.getHttpVersion().equals("HTTP_1_1")) {
            builder.protocols(Arrays.asList(Protocol.HTTP_1_1));
        }
        this.httpClient = builder.build();

        YamlConfiguration profileConfig = Main.getProfileConfig();
        this.isPrivateCache = CacheBuilder.newBuilder()
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
        try (Response resp = httpClient.newCall(new Request.Builder()
                .url(apiEndpoint + "/auth/login")
                .post(new FormBody.Builder()
                        .add("username", config.getUsername())
                        .add("password", config.getPassword())
                        .build())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()).execute()) {
            if (resp.code() == 200 && isLoggedIn()) {
                return new DownloaderLoginResult(DownloaderLoginResult.Status.SUCCESS, new TranslationComponent(Lang.STATUS_TEXT_OK));
            }
            return new DownloaderLoginResult(DownloaderLoginResult.Status.INCORRECT_CREDENTIAL, new TranslationComponent(Lang.DOWNLOADER_LOGIN_EXCEPTION, resp.body().string()));
            // return request.statusCode() == 200;
        } catch (IOException e) {
            return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_LOGIN_IO_EXCEPTION, e.getClass().getName() + ": " + e.getMessage()));
        }
    }

    @Override
    public String getEndpoint() {
        return apiEndpoint;
    }


    public boolean isLoggedIn() {
        try (Response resp = httpClient.newCall(new Request.Builder()
                .url(apiEndpoint + "/app/version")
                .build()).execute()) {
            return resp.code() == 200;
        } catch (IOException e) {
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
        List<QBittorrentTorrent> qbTorrent;
        try (Response resp = httpClient.newCall(new Request.Builder()
                .url(apiEndpoint + "/torrents/info?filter=active")
                .build()).execute()) {
            if (resp.code() != 200) {
                throw new IllegalStateException(tlUI(Lang.DOWNLOADER_QB_FAILED_REQUEST_TORRENT_LIST, resp.code(), resp.body().string()));
            }
            qbTorrent = JsonUtil.getGson().fromJson(resp.body().string(), new TypeToken<List<QBittorrentTorrent>>() {
            }.getType());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        if (config.isIgnorePrivate()) {
            fillTorrentPrivateField(qbTorrent);
        }
        return qbTorrent.stream().map(t -> (Torrent) t)
                .filter(t-> !config.isIgnorePrivate() || !t.isPrivate())
                .collect(Collectors.toList());
    }

    protected void fillTorrentPrivateField(List<QBittorrentTorrent> qbTorrent) {
        Semaphore privateStatusLimit = new Semaphore(5);
        try (ExecutorService service = Executors.newVirtualThreadPerTaskExecutor()) {
            qbTorrent.stream()
                    .filter(torrent -> torrent.getPrivateTorrent() == null)
                    .forEach(detail -> service.submit(() -> {
                        if (detail.getPrivateTorrent() == null) {
                            try {
                                privateStatusLimit.acquire();
                                detail.setPrivateTorrent(getPrivateStatus(detail));
                            } catch (Exception e) {
                                log.debug("Failed to load private cache", e);
                            } finally {
                                privateStatusLimit.release();
                            }
                        }
                    }));
        }
    }

    protected Boolean getPrivateStatus(QBittorrentTorrent torrent) {
        if (torrent.getPrivateTorrent() != null) {
            return torrent.getPrivateTorrent();
        }
        try {
            return isPrivateCache.get(torrent.getHash(), () -> {
                try (Response resp = httpClient.newCall(new Request.Builder()
                        .url(apiEndpoint + "/torrents/properties?hash=" + torrent.getHash())
                        .build()).execute()) {
                    log.debug("Field is_private is not present and cache miss, query from properties api, hash: {}", torrent.getHash());
                    if (resp.code() == 200) {
                        var newDetail = JsonUtil.getGson().fromJson(resp.body().string(), QBittorrentTorrent.class);
                        return newDetail.getPrivateTorrent();
                    } else {
                        log.warn("Error fetching properties for torrent hash: {}, status: {}", torrent.getHash(), resp.code());
                    }
                } catch (IOException e) {
                    log.warn("Error fetching properties for torrent hash: {}", torrent.getHash(), e);
                }
                return null;
            });
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public DownloaderStatistics getStatistics() {
        try (Response resp = httpClient.newCall(new Request.Builder()
                .url(apiEndpoint + "/sync/maindata")
                .build()).execute()) {
            if (resp.code() != 200) {
                throw new IllegalStateException(tlUI(Lang.DOWNLOADER_FAILED_REQUEST_STATISTICS, resp.code(), resp.body().string()));
            }
            QBittorrentMainData mainData = JsonUtil.getGson().fromJson(resp.body().string(), QBittorrentMainData.class);
            return new DownloaderStatistics(mainData.getServerState().getAlltimeUl(), mainData.getServerState().getAlltimeDl());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<Peer> getPeers(Torrent torrent) {
        String body;
        try (Response resp = httpClient.newCall(new Request.Builder()
                .url(apiEndpoint + "/sync/torrentPeers?hash=" + torrent.getId())
                .build()).execute()) {
            if (resp.code() != 200) {
                throw new IllegalStateException(tlUI(Lang.DOWNLOADER_QB_FAILED_REQUEST_PEERS_LIST_IN_TORRENT, resp.code(), resp.body().string()));
            }
            body = resp.body().string();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        JsonObject object = JsonParser.parseString(body).getAsJsonObject();
        JsonObject peers = object.getAsJsonObject("peers");
        List<Peer> peersList = new ArrayList<>();
        for (String s : peers.keySet()) {
            JsonObject singlePeerObject = peers.getAsJsonObject(s);
            QBittorrentPeer qbPeer = JsonUtil.getGson().fromJson(singlePeerObject.toString(), QBittorrentPeer.class);
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
            try (Response resp = httpClient.newCall(new Request.Builder()
                    .url(apiEndpoint + "/transfer/banPeers")
                    .post(new FormBody.Builder()
                            .add("hash", hash)
                            .add("peers", peers.toString())
                            .build())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build()).execute()) {
                if (resp.code() != 200) {
                    log.error(tlUI(Lang.DOWNLOADER_QB_INCREAMENT_BAN_FAILED, name, apiEndpoint, resp.code(), "HTTP ERROR", resp.body().string()));
                    throw new IllegalStateException("Save qBittorrent banlist error: statusCode=" + resp.code());
                }
            } catch (IOException e) {
                log.error(tlUI(Lang.DOWNLOADER_QB_INCREAMENT_BAN_FAILED, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
                throw new IllegalStateException(e);
            }
        });
    }

    protected void setBanListFull(Collection<PeerAddress> peerAddresses) {
        StringJoiner joiner = new StringJoiner("\n");
        peerAddresses.forEach(p -> joiner.add(p.getIp()));
        try (Response resp = httpClient.newCall(new Request.Builder()
                .url(apiEndpoint + "/app/setPreferences")
                .post(new FormBody.Builder()
                        .add("json", JsonUtil.getGson().toJson(Map.of("banned_IPs", joiner.toString())))
                        .build())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()).execute()) {
            if (resp.code() != 200) {
                log.error(tlUI(Lang.DOWNLOADER_QB_FAILED_SAVE_BANLIST, name, apiEndpoint, resp.code(), "HTTP ERROR", resp.body().string()));
                throw new IllegalStateException("Save qBittorrent banlist error: statusCode=" + resp.code());
            }
        } catch (IOException e) {
            log.error(tlUI(Lang.DOWNLOADER_QB_FAILED_SAVE_BANLIST, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
            throw new IllegalStateException(e);
        }
    }


    @Override
    public void close() throws Exception {

    }


}
