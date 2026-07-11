package com.ghostchu.peerbanhelper.downloader.impl.aria2next;

import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.peer.PeerImpl;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.bittorrent.torrent.TorrentImpl;
import com.ghostchu.peerbanhelper.bittorrent.tracker.Tracker;
import com.ghostchu.peerbanhelper.bittorrent.tracker.TrackerImpl;
import com.ghostchu.peerbanhelper.downloader.AbstractDownloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderFeatureFlag;
import com.ghostchu.peerbanhelper.downloader.DownloaderLoginResult;
import com.ghostchu.peerbanhelper.downloader.DownloaderSpeedLimiter;
import com.ghostchu.peerbanhelper.downloader.exception.DownloaderRequestException;
import com.ghostchu.peerbanhelper.downloader.impl.aria2next.bean.*;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.jsonrpc.JsonRpcRequest;
import com.ghostchu.peerbanhelper.util.jsonrpc.JsonRpcResponse;
import com.ghostchu.peerbanhelper.util.traversal.NatAddressProvider;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.gson.JsonObject;
import com.google.gson.internal.GsonTypes;
import com.google.gson.reflect.TypeToken;
import com.vdurmont.semver4j.Semver;
import inet.ipaddr.IPAddress;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.*;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

public final class Aria2Next extends AbstractDownloader {
    private final OkHttpClient httpClient;
    private final Config config;
    private Semver lastSemver;

    public Aria2Next(String id, Config config, AlertManager alertManager, HTTPUtil httpUtil, NatAddressProvider natAddressProvider) {
        super(id, alertManager, natAddressProvider);
        this.config = config;
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

        var builder = httpUtil.newBuilderForDownloader()
                .connectionPool(new ConnectionPool(getMaxConcurrentPeerRequestSlots() + 10, 5, TimeUnit.MINUTES))
                .addInterceptor(chain -> {
                    Request originalRequest = chain.request();
                    Request newRequest = originalRequest.newBuilder()
                            .header("Content-Type", "application/json")
                            .header("Authorization", "token:" + config.getToken())
                            .build();
                    return chain.proceed(newRequest);
                });
        httpUtil.disableSSLVerify(builder, !config.isVerifySsl());
        this.httpClient = builder.build();
    }


    public static Aria2Next loadFromConfig(String id, JsonObject section, AlertManager alertManager, HTTPUtil httpUtil, NatAddressProvider natAddressProvider) {
        Aria2Next.Config config = JsonUtil.getGson().fromJson(section.toString(), Aria2Next.Config.class);
        return new Aria2Next(id, config, alertManager, httpUtil, natAddressProvider);
    }

    public static Aria2Next loadFromConfig(String id, ConfigurationSection section, AlertManager alertManager, HTTPUtil httpUtil, NatAddressProvider natAddressProvider) {
        Aria2Next.Config config = Aria2Next.Config.readFromYaml(section, id);
        return new Aria2Next(id, config, alertManager, httpUtil, natAddressProvider);
    }

    @Override
    public @NotNull List<DownloaderFeatureFlag> getFeatureFlags() {
        return List.of(DownloaderFeatureFlag.UNBAN_IP,
                DownloaderFeatureFlag.TRAFFIC_STATS,
                DownloaderFeatureFlag.RANGE_BAN_IP);
    }


    @Override
    public DownloaderLoginResult login0() {
        var req = buildRpcRequest("aria2.getVersion", null);
        try (Response response = httpClient.newCall(req).execute()) {
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                Type responseType = new TypeToken<JsonRpcResponse<A2Version>>() {
                }.getType();
                JsonRpcResponse<A2Version> rpcResponse = JsonUtil.standard().fromJson(responseBody, responseType);
                var result = rpcResponse.getResult();
                if (result != null && result.getVersion() != null) {
                    this.lastSemver = new Semver(result.getVersion(), Semver.SemverType.LOOSE);
                    return new DownloaderLoginResult(DownloaderLoginResult.Status.SUCCESS, new TranslationComponent(Lang.STATUS_TEXT_OK));
                }
            } else {
                if (response.code() == 401 || response.code() == 403) {
                    return new DownloaderLoginResult(DownloaderLoginResult.Status.INCORRECT_CREDENTIAL, new TranslationComponent(Lang.DOWNLOADER_LOGIN_INCORRECT_CRED));
                }
            }
            return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_LOGIN_EXCEPTION, "statusCode=" + response.code()));
        } catch (Exception e) {
            return new DownloaderLoginResult(DownloaderLoginResult.Status.NETWORK_ERROR, new TranslationComponent(Lang.DOWNLOADER_LOGIN_IO_EXCEPTION, e.getClass().getName() + ": " + e.getMessage()));
        }
    }

    @Override
    public @NotNull JsonObject saveDownloaderJson() {
        return JsonUtil.getGson().toJsonTree(config).getAsJsonObject();
    }

    @Override
    public @NotNull YamlConfiguration saveDownloader() {
        return config.saveToYaml();
    }


    @Override
    public @NotNull String getEndpoint() {
        return config.getEndpoint();
    }

    @Override
    public @NotNull String getName() {
        return config.getName();
    }

    @Override
    public @NotNull String getType() {
        return "Aria2Next";
    }

    @Override
    public boolean isPaused() {
        return config.isPaused();
    }

    @Override
    public @NotNull List<? extends Torrent> getTorrents() {
        var req = buildRpcRequest("aria2.tellActive", List.of(
                "gid", "status", "totalLength", "completedLength",
                "uploadLength", "bitfield", "downloadSpeed",
                "uploadSpeed", "infoHash", "numSeeders",
                "seeder", "pieceLength", "numPieces", "connections",
                "errorCode", "errorMessage", "followedBy", "following", "belongsTo",
                "dir", "bittorrent", "verifiedLength", "verifyIntegrityPending", "files"
        ));
        return sendRpcRequestList(req, A2Task.class);
    }

    @Override
    public @NotNull List<? extends Torrent> getAllTorrents() {
        var active = buildRpcRequest("aria2.tellActive", List.of(
                "gid", "status", "totalLength", "completedLength",
                "uploadLength", "bitfield", "downloadSpeed",
                "uploadSpeed", "infoHash", "numSeeders",
                "seeder", "pieceLength", "numPieces", "connections",
                "errorCode", "errorMessage", "followedBy", "following", "belongsTo",
                "dir", "bittorrent", "verifiedLength", "verifyIntegrityPending", "files"
        ));
        List<A2Task> torrents = new ArrayList<>(sendRpcRequestList(active, A2Task.class));
        var waiting = buildRpcRequest("aria2.tellWaiting", List.of(
                "gid", "status", "totalLength", "completedLength",
                "uploadLength", "bitfield", "downloadSpeed",
                "uploadSpeed", "infoHash", "numSeeders",
                "seeder", "pieceLength", "numPieces", "connections",
                "errorCode", "errorMessage", "followedBy", "following", "belongsTo",
                "dir", "bittorrent", "verifiedLength", "verifyIntegrityPending", "files"
        ));
        torrents.addAll(sendRpcRequestList(waiting, A2Task.class));
        var stopped = buildRpcRequest("aria2.tellStopped", List.of(
                "gid", "status", "totalLength", "completedLength",
                "uploadLength", "bitfield", "downloadSpeed",
                "uploadSpeed", "infoHash", "numSeeders",
                "seeder", "pieceLength", "numPieces", "connections",
                "errorCode", "errorMessage", "followedBy", "following", "belongsTo",
                "dir", "bittorrent", "verifiedLength", "verifyIntegrityPending", "files"
        ));
        torrents.addAll(sendRpcRequestList(stopped, A2Task.class));
        return torrents;
    }

    @Override
    public @NotNull List<? extends Peer> getPeers(@NotNull Torrent torrent) {
        var requestPeers = buildRpcRequest("aria2.getPeers", List.of(
                torrent.getId()
        ));
        // Aria2 出于某种原因决定用 [ [ {peerobject} ] ] 的方式返回数据，我直接似了
        List<A2Peer> peers;

       return peers;
    }

    @Override
    public @NotNull List<? extends Tracker> getTrackers(@NotNull Torrent torrent) {
        return ((A2Task)torrent).getBittorrent().getAnnounceList().stream()
                .map(TrackerImpl::new)
                .toList();
    }

    @Override
    public void setTrackers(@NotNull Torrent torrent, @NotNull List<Tracker> trackers) {

    }

    @Override
    public void setBanList(@NotNull Collection<IPAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed, boolean applyFullList) {

    }

    @Override
    public DownloaderSpeedLimiter getSpeedLimiter() {
        var globalOptions = getGlobalOptions();
        return new DownloaderSpeedLimiter(globalOptions.getMaxUploadLimit(), globalOptions.getMaxDownloadLimit());
    }

    @Override
    public void setSpeedLimiter(@NotNull DownloaderSpeedLimiter speedLimiter) {

    }

    @Override
    public int getBTProtocolPort() {
        return -1;
    }

    @Override
    public void setBTProtocolPort(int port) {
        // do nothing
    }

    @Override
    public void close() {

    }

    private A2GlobalOptions getGlobalOptions() {
        var req = buildRpcRequest("aria2.getGlobalOption", null);
        return sendRpcRequest(req, A2GlobalOptions.class);
    }


    private <T> T sendRpcRequest(Request request, Class<T> clazz) {
        try (Response resp = httpClient.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new DownloaderRequestException(tlUI(Lang.DOWNLOADER_UNHANDLED_EXCEPTION, getName(), resp.code(), resp.body()));
            }
            String responseBody = resp.body().string();
            Type responseType = GsonTypes.newParameterizedTypeWithOwner(null, JsonRpcResponse.class, clazz);
            JsonRpcResponse<T> response = JsonUtil.standard().fromJson(responseBody, responseType);
            if (response.getResult() != null) {
                return response.getResult();
            }
            throw new DownloaderRequestException(tlUI(Lang.DOWNLOADER_JSONRPC_REQUEST_FAILED, config.getName(), config.getEndpoint(), resp.code(), response.getError().getCode(), response.getError().getMessage()));
        } catch (Exception e) {
            throw new DownloaderRequestException(e);
        }
    }


    private <T> List<T> sendRpcRequestList(Request request, Class<T> clazz) {
        try (Response resp = httpClient.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                // 注意：resp.body().string() 只能调用一次，这里如果用了，后面解析就会拿不到数据
                // 建议这里只打印 code，或者先把 body 存成字符串
                String errorBody = resp.body() != null ? resp.body().string() : "";
                throw new DownloaderRequestException(tlUI(Lang.DOWNLOADER_UNHANDLED_EXCEPTION, getName(), resp.code(), errorBody));
            }
            String responseBody = resp.body().string();
            Type listType = GsonTypes.newParameterizedTypeWithOwner(null, List.class, clazz);
            Type responseType = GsonTypes.newParameterizedTypeWithOwner(null, JsonRpcResponse.class, listType);
            JsonRpcResponse<List<T>> response = JsonUtil.standard().fromJson(responseBody, responseType);
            if (response.getResult() != null) {
                return response.getResult(); // 此时 result 的实际类型就是 List<T> 了
            }
            throw new DownloaderRequestException(tlUI(Lang.DOWNLOADER_JSONRPC_REQUEST_FAILED, config.getName(), config.getEndpoint(), resp.code(), response.getError().getCode(), response.getError().getMessage()));
        } catch (Exception e) {
            throw new DownloaderRequestException(e);
        }
    }

    /**
     * 统一的 RPC 请求发送方法
     *
     * @param method       接口名，如 "aria2.addUri"
     * @param customParams 该接口特有的后续参数
     */
    private Request buildRpcRequest(String method, List<Object> customParams) {
        List<Object> finalParams = new ArrayList<>();
        String token = this.config.getToken();
        if (token != null && !token.isEmpty()) {
            finalParams.add("token:" + token);
        }
        if (customParams != null) {
            finalParams.addAll(customParams);
        }
        JsonRpcRequest rpcRequest = new JsonRpcRequest(
                UUID.randomUUID().toString(),
                method,
                finalParams
        );
        String jsonPayload = JsonUtil.standard().toJson(rpcRequest);
        RequestBody body = RequestBody.create(jsonPayload, MediaType.get("application/json; charset=utf-8"));
        return new Request.Builder().url(config.getEndpoint()).post(body).build();
    }

    @NoArgsConstructor
    @Data
    public static class Config {
        private String name;
        private String type;
        private String endpoint;
        private String token;
        private boolean incrementBan;
        private boolean verifySsl;
        private boolean ignorePrivate;
        private boolean paused;
        private boolean includeEd2k;

        public static Aria2Next.Config readFromYaml(ConfigurationSection section, String alternativeName) {
            Aria2Next.Config config = new Aria2Next.Config();
            config.setType("Aria2Next");
            config.setEndpoint(section.getString("endpoint"));
            if (config.getEndpoint().endsWith("/")) { // 浏览器复制党 workaround 一下， 避免连不上的情况
                config.setEndpoint(config.getEndpoint().substring(0, config.getEndpoint().length() - 1));
            }
            config.setName(section.getString("name", alternativeName));
            config.setToken(section.getString("token", ""));
            config.setIncrementBan(section.getBoolean("increment-ban", true));
            config.setVerifySsl(section.getBoolean("verify-ssl", true));
            config.setIgnorePrivate(section.getBoolean("ignore-private", false));
            config.setPaused(section.getBoolean("paused", false));
            config.setIncludeEd2k(section.getBoolean("include-ed2k", false));
            return config;
        }

        public YamlConfiguration saveToYaml() {
            YamlConfiguration section = new YamlConfiguration();
            section.set("type", "Aria2Next");
            section.set("name", name);
            section.set("endpoint", endpoint);
            section.set("token", token);
            section.set("increment-ban", incrementBan);
            section.set("ignore-private", ignorePrivate);
            section.set("verify-ssl", verifySsl);
            section.set("paused", paused);
            section.set("include-ed2k", includeEd2k);
            return section;
        }
    }
}
