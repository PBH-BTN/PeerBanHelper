package com.ghostchu.peerbanhelper.downloader.impl.aria2next;

import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
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
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.vdurmont.semver4j.Semver;
import inet.ipaddr.IPAddress;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
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
        try {
            var req = buildRpcRequest("aria2.tellActive", List.of(List.of(
                    "gid", "status", "totalLength", "completedLength",
                    "uploadLength", "bitfield", "downloadSpeed",
                    "uploadSpeed", "infoHash", "numSeeders",
                    "seeder", "pieceLength", "numPieces", "connections",
                    "errorCode", "errorMessage", "followedBy", "following", "belongsTo",
                    "dir", "bittorrent", "verifiedLength", "verifyIntegrityPending", "files"
            )));
            return sendRpcRequest(req, new TypeToken<List<A2Task>>() {
            });
        } catch (DownloaderRequestException e) {
            log.error("Error on request", e);
            return Collections.emptyList();
        }
    }

    @Override
    public @NotNull List<? extends Torrent> getAllTorrents() {
        List<A2Task> torrents = new ArrayList<>(20);
        try {
            var active = buildRpcRequest("aria2.tellActive", List.of(List.of(
                    "gid", "status", "totalLength", "completedLength",
                    "uploadLength", "bitfield", "downloadSpeed",
                    "uploadSpeed", "infoHash", "numSeeders",
                    "seeder", "pieceLength", "numPieces", "connections",
                    "errorCode", "errorMessage", "followedBy", "following", "belongsTo",
                    "dir", "bittorrent", "verifiedLength", "verifyIntegrityPending", "files"
            )));
            torrents.addAll(sendRpcRequest(active, new TypeToken<List<A2Task>>() {
            }));
            var waiting = buildRpcRequest("aria2.tellWaiting", List.of(List.of(
                    "gid", "status", "totalLength", "completedLength",
                    "uploadLength", "bitfield", "downloadSpeed",
                    "uploadSpeed", "infoHash", "numSeeders",
                    "seeder", "pieceLength", "numPieces", "connections",
                    "errorCode", "errorMessage", "followedBy", "following", "belongsTo",
                    "dir", "bittorrent", "verifiedLength", "verifyIntegrityPending", "files"
            )));
            torrents.addAll(sendRpcRequest(waiting, new TypeToken<List<A2Task>>() {
            }));
            var stopped = buildRpcRequest("aria2.tellStopped", List.of(List.of(
                    "gid", "status", "totalLength", "completedLength",
                    "uploadLength", "bitfield", "downloadSpeed",
                    "uploadSpeed", "infoHash", "numSeeders",
                    "seeder", "pieceLength", "numPieces", "connections",
                    "errorCode", "errorMessage", "followedBy", "following", "belongsTo",
                    "dir", "bittorrent", "verifiedLength", "verifyIntegrityPending", "files"
            )));
            torrents.addAll(sendRpcRequest(stopped, new TypeToken<List<A2Task>>() {
            }));
            return torrents;
        } catch (DownloaderRequestException e) {
            log.error("Error on request", e);
            return torrents;
        }
    }

    @Override
    public @NotNull List<? extends Peer> getPeers(@NotNull Torrent torrent) {
        try {
            var requestPeers = buildRpcRequest("aria2.getPeers", List.of(
                    torrent.getId()
            ));
            return sendRpcRequest(requestPeers, new TypeToken<List<A2Peer>>() {
            });
        } catch (DownloaderRequestException e) {
            log.error("Error on request", e);
            return Collections.emptyList();
        }
    }

    @Override
    public @NotNull List<? extends Tracker> getTrackers(@NotNull Torrent torrent) {
        return ((A2Task) torrent).getBittorrent().getAnnounceList().stream()
                .map(TrackerImpl::new)
                .toList();
    }

    @Override
    public void setTrackers(@NotNull Torrent torrent, @NotNull List<? extends Tracker> trackers) {

    }

    @Override
    public void setBanList(@NotNull Collection<IPAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed, boolean applyFullList) {
        String text = fullList.stream()
                        .flatMap(i->remapBanListAddress(i, true).stream())
                .map(IPAddress::toCompressedString)
                                .collect(Collectors.joining("\n"));
        try {
            var setBanList = sendRpcRequest(buildRpcRequest("aria2.changeGlobalOption",
                    List.of(Map.of("bt-peer-blocklist", text))
            ), new TypeToken<>() {
            });
        }catch (DownloaderRequestException e){
            log.error("Error on request", e);
        }
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

    private A2GlobalOptions getGlobalOptions() throws DownloaderRequestException {
        var req = buildRpcRequest("aria2.getGlobalOption", null);
        return sendRpcRequest(req, A2GlobalOptions.class);
    }


    private <T> T sendRpcRequest(Request request, Type dataType) throws DownloaderRequestException {
        try (Response resp = httpClient.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new DownloaderRequestException(tlUI(Lang.DOWNLOADER_JSONRPC_REQUEST_FAILED, getName(), getEndpoint(), resp.code(), "N/A", resp.body() != null ? resp.body().string() : ""));
            }

            String responseBody = resp.body().string();
            System.out.println(responseBody);
            // 使用 Gson 的 TypeToken.getParameterized 构建 JsonRpcResponse<dataType>
            Type responseType = TypeToken.getParameterized(JsonRpcResponse.class, dataType).getType();
            JsonRpcResponse<T> response = JsonUtil.standard().fromJson(responseBody, responseType);

            // 正确的 JsonRPC 判断标准：优先看 error 字段是否非空
            if (response.getError() != null) {
                throw new DownloaderRequestException(tlUI(
                        Lang.DOWNLOADER_JSONRPC_REQUEST_FAILED,
                        config.getName(),
                        config.getEndpoint(),
                        resp.code(),
                        response.getError().getCode(),
                        response.getError().getMessage()
                ));
            }

            return response.getResult();
        } catch (Exception e) {
            if (e instanceof DownloaderRequestException ex) {
                throw ex;
            }
            throw new DownloaderRequestException(e);
        }
    }

    /**
     * 重载方法：兼容传入普通 Class 的情况（如 String.class, MyDTO.class）
     */
    private <T> T sendRpcRequest(Request request, Class<T> clazz) throws DownloaderRequestException {
        return sendRpcRequest(request, (Type) clazz);
    }

    /**
     * 重载方法：支持通过 TypeToken 传入复杂泛型类型（如 List<User>, List<List<String>>）
     */
    private <T> T sendRpcRequest(Request request, TypeToken<T> typeToken) throws DownloaderRequestException {
        return sendRpcRequest(request, typeToken.getType());
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
            config.setType("aria2next");
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
            section.set("type", "aria2next");
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
