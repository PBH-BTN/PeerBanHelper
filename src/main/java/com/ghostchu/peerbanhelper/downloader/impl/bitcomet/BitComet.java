package com.ghostchu.peerbanhelper.downloader.impl.bitcomet;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.AbstractDownloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLoginResult;
import com.ghostchu.peerbanhelper.downloader.DownloaderStatistics;
import com.ghostchu.peerbanhelper.downloader.impl.bitcomet.crypto.BCAESTool;
import com.ghostchu.peerbanhelper.downloader.impl.bitcomet.resp.*;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.peer.PeerImpl;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.torrent.TorrentImpl;
import com.ghostchu.peerbanhelper.util.ByteUtil;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.gson.JsonObject;
import com.vdurmont.semver4j.Semver;
import inet.ipaddr.HostName;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.Interceptor;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.ghostchu.peerbanhelper.text.Lang.DOWNLOADER_BC_FAILED_SAVE_BANLIST;
import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;
import static com.ghostchu.peerbanhelper.util.HTTPUtil.MEDIA_TYPE_JSON;

public class BitComet extends AbstractDownloader {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(BitComet.class);
    private static final UUID clientId = UUID.nameUUIDFromBytes("PeerBanHelper".getBytes(StandardCharsets.UTF_8));
    protected final String apiEndpoint;
    protected final OkHttpClient httpClient;
    private final Config config;
    private String deviceToken;
    private String serverId;
    private String serverVersion;
    private String serverName;
    private boolean dependenciesLoaded = false;

    public BitComet(String name, Config config) {
        super(name);
        try {
            loadRequiredDependencies();
            BCAESTool.init();
            dependenciesLoaded = true;
        } catch (IOException e) {
            log.error(tlUI(Lang.DOWNLOADER_BC_DOWNLOAD_DEPENDENCIES_FAILED));
        } catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException | InstantiationException |
                 IllegalAccessException e) {
            log.error("Unable to load BCAESTool");
        }
        this.config = config;
        this.apiEndpoint = config.getEndpoint();
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        OkHttpClient.Builder builder = Main.getSharedHttpClient()
                .newBuilder()
                .followRedirects(true)
                .connectTimeout(Duration.of(15, ChronoUnit.SECONDS))
                .readTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .cookieJar(new JavaNetCookieJar(cm))
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Interceptor.Chain chain) throws IOException {
                        Request original = chain.request();
                        Request request = original.newBuilder()
                                .header("Accept-Encoding", "gzip,deflate")
                                .header("Content-Type", "application/json")
                                .header("Client-Type", "BitComet WebUI")
                                .header("User-Agent", "PeerBanHelper BitComet Adapter")
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
    }

    public static BitComet loadFromConfig(String name, ConfigurationSection section) {
        Config config = Config.readFromYaml(section);
        return new BitComet(name, config);
    }

    public static BitComet loadFromConfig(String name, JsonObject section) {
        Config config = JsonUtil.getGson().fromJson(section, Config.class);
        return new BitComet(name, config);
    }

    private static PeerAddress parseAddress(String address, int port, int listenPort) {
        address = address.trim();
        HostName host = new HostName(address);
        return new PeerAddress(host.getHost(), port);
    }

    private void loadRequiredDependencies() throws IOException {
        Main.loadDependencies("/libraries/bitcomet.maven");
    }

    @Override
    public JsonObject saveDownloaderJson() {
        return JsonUtil.getGson().toJsonTree(config).getAsJsonObject();
    }

    @Override
    public YamlConfiguration saveDownloader() {
        return config.saveToYaml();
    }

    public DownloaderLoginResult login0() throws Exception {
        if (!dependenciesLoaded)
            return new DownloaderLoginResult(DownloaderLoginResult.Status.REQUIRE_TAKE_ACTIONS, new TranslationComponent(Lang.DOWNLOADER_BC_DOWNLOAD_DEPENDENCIES_FAILED));
        if (isLoggedIn())
            return new DownloaderLoginResult(DownloaderLoginResult.Status.SUCCESS, new TranslationComponent(Lang.STATUS_TEXT_OK)); // 重用 Session 会话
        Map<String, String> loginAttemptCred = new HashMap<>();
        loginAttemptCred.put("username", config.getUsername());
        loginAttemptCred.put("password", config.getPassword());
        String aesEncrypted = BCAESTool.credential(JsonUtil.standard().toJson(loginAttemptCred), clientId.toString());
        Map<String, String> loginJsonObject = new HashMap<>();
        loginJsonObject.put("authentication", aesEncrypted);
        loginJsonObject.put("client_id", clientId.toString());
        String inviteToken;
        try (Response resp = httpClient.newCall(new Request.Builder()
                .url(apiEndpoint + BCEndpoint.USER_LOGIN.getEndpoint())
                .post(RequestBody.create(JsonUtil.standard().toJson(loginJsonObject), MEDIA_TYPE_JSON))
                .build()).execute()) {
            BCLoginResponse loginResponse = JsonUtil.standard().fromJson(resp.body().string(), BCLoginResponse.class);
            if (loginResponse.getErrorCode().equalsIgnoreCase("PASSWORD_ERROR")) {
                return new DownloaderLoginResult(DownloaderLoginResult.Status.INCORRECT_CREDENTIAL, new TranslationComponent(Lang.DOWNLOADER_LOGIN_EXCEPTION, resp.body().string()));
            }
            if (!loginResponse.getErrorCode().equalsIgnoreCase("ok")) {
                return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_LOGIN_EXCEPTION, resp.body().string()));
            }
            // 进行版本检查
            boolean bcVerAcceptable = false;
            var bcSemver = new Semver(loginResponse.getVersion(), Semver.SemverType.LOOSE);
            if (bcSemver.getMajor() > 2) {
                bcVerAcceptable = true;
            } else {
                if (bcSemver.getMajor() == 2) {
                    if (bcSemver.getMinor() >= 10) {
                        bcVerAcceptable = true;
                    }
                }
            }
            if (!bcVerAcceptable) {
                return new DownloaderLoginResult(DownloaderLoginResult.Status.MISSING_COMPONENTS, new TranslationComponent(Lang.DOWNLOADER_BC_VERSION_UNACCEPTABLE, resp.body().string()));
            }
            inviteToken = loginResponse.getInviteToken();
        } catch (IOException e) {
            return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_LOGIN_IO_EXCEPTION, e.getClass().getName() + ": " + e.getMessage()));
        }

        Map<String, String> inviteTokenRetrievePayload = new HashMap<>();
        inviteTokenRetrievePayload.put("device_id", clientId.toString());
        inviteTokenRetrievePayload.put("device_name", "PeerBanHelper - BitComet Adapter");
        inviteTokenRetrievePayload.put("invite_token", inviteToken);
        inviteTokenRetrievePayload.put("platform", "webui");
        try (Response retrieveDeviceToken = httpClient.newCall(new Request.Builder()
                .url(apiEndpoint + BCEndpoint.GET_DEVICE_TOKEN.getEndpoint())
                .post(RequestBody.create(JsonUtil.standard().toJson(inviteTokenRetrievePayload), MEDIA_TYPE_JSON))
                .header("Authorization", "Bearer " + inviteToken)
                .build()).execute()) {
            var deviceTokenResponse = JsonUtil.standard().fromJson(retrieveDeviceToken.body().string(), BCDeviceTokenResult.class);
            this.deviceToken = deviceTokenResponse.getDeviceToken();
            this.serverId = deviceTokenResponse.getServerId();
            this.serverVersion = deviceTokenResponse.getVersion();
            this.serverName = deviceTokenResponse.getServerName();
            if (queryNeedReConfigureIpFilter()) {
                enableIpFilter();
            }
            return new DownloaderLoginResult(DownloaderLoginResult.Status.SUCCESS, new TranslationComponent(Lang.STATUS_TEXT_OK));
        } catch (IOException e) {
            return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_LOGIN_IO_EXCEPTION, e.getClass().getName() + ": " + e.getMessage()));
        }
    }

    @Override
    public String getEndpoint() {
        return apiEndpoint;
    }

    @Override
    public String getType() {
        return "BitComet";
    }

    public boolean isLoggedIn() {
        try {
            getTorrents();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean queryNeedReConfigureIpFilter() throws IOException {
        try (Response query = httpClient.newCall(new Request.Builder()
                .url(apiEndpoint + BCEndpoint.GET_IP_FILTER_CONFIG.getEndpoint())
                .post(RequestBody.create("{}", MEDIA_TYPE_JSON))
                .header("Authorization", "Bearer " + this.deviceToken)
                .build()).execute()) {
            var resp = JsonUtil.standard().fromJson(query.body().string(), BCIpFilterResponse.class);
            return !resp.getIpFilterConfig().getEnableIpFilter() || resp.getIpFilterConfig().getEnableWhitelistMode();
        }
    }

    private void enableIpFilter() throws IOException {
        log.info(tlUI(Lang.DOWNLOADER_BC_CONFIG_IP_FILTER));
        Map<String, Object> settings = new HashMap<>() {{
            put("ip_filter_config", new HashMap<>() {{
                put("enable_ip_filter", true);
                put("enable_whitelist_mode", false);
            }});
        }};
        try (Response resp = httpClient.newCall(new Request.Builder()
                .url(apiEndpoint + BCEndpoint.GET_IP_FILTER_CONFIG.getEndpoint())
                .post(RequestBody.create(JsonUtil.standard().toJson(settings), MEDIA_TYPE_JSON))
                .header("Authorization", "Bearer " + this.deviceToken)
                .build()).execute()) {
            var configResp = JsonUtil.standard().fromJson(resp.body().string(), BCConfigSetResponse.class);
            if ("ok".equalsIgnoreCase(configResp.getErrorCode())) {
                log.info(tlUI(Lang.DOWNLOADER_BC_CONFIG_IP_FILTER_SUCCESS));
            } else {
                log.error(tlUI(Lang.DOWNLOADER_BC_CONFIG_IP_FILTER_FAILED));
            }
        }
    }

    @Override
    public List<Torrent> getTorrents() {
        Map<String, String> requirements = new HashMap<>();
        requirements.put("group_state", "ACTIVE");
        requirements.put("sort_key", "");
        requirements.put("sort_order", "unsorted");
        try (Response resp = httpClient.newCall(new Request.Builder()
                .url(apiEndpoint + BCEndpoint.GET_TASK_LIST.getEndpoint())
                .post(RequestBody.create(JsonUtil.standard().toJson(requirements), MEDIA_TYPE_JSON))
                .header("Authorization", "Bearer " + this.deviceToken)
                .build()).execute()) {
            if (resp.code() != 200) {
                throw new IllegalStateException(tlUI(Lang.DOWNLOADER_BC_FAILED_REQUEST_TORRENT_LIST, resp.code(), resp.body().string()));
            }
            var response = JsonUtil.standard().fromJson(resp.body().string(), BCTaskListResponse.class);
            return response.getTasks().stream()
                    .filter(t -> t.getType().equals("BT"))
                    .map(torrent -> {
                        Map<String, String> taskIds = new HashMap<>();
                        taskIds.put("task_id", torrent.getTaskId().toString());
                        try (Response fetch = httpClient.newCall(new Request.Builder()
                                .url(apiEndpoint + BCEndpoint.GET_TASK_SUMMARY.getEndpoint())
                                .post(RequestBody.create(JsonUtil.standard().toJson(taskIds), MEDIA_TYPE_JSON))
                                .header("Authorization", "Bearer " + this.deviceToken)
                                .build()).execute()) {
                            return JsonUtil.standard().fromJson(fetch.body().string(), BCTaskTorrentResponse.class);
                        } catch (IOException e) {
                            log.warn("Unable to fetch task details", e);
                            return null;
                        }
                    }).filter(Objects::nonNull)
                    .map(torrent -> new TorrentImpl(torrent.getTask().getTaskId().toString(),
                            torrent.getTask().getTaskName(),
                            torrent.getTaskDetail().getInfohash() != null ? torrent.getTaskDetail().getInfohash() : torrent.getTaskDetail().getInfohashV2(),
                            torrent.getTaskDetail().getTotalSize(),
                            torrent.getTaskStatus().getDownloadPermillage() / 1000.0d,
                            torrent.getTask().getUploadRate(),
                            torrent.getTask().getDownloadRate(),
                            torrent.getTaskDetail().getTorrentPrivate()
                    )).collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public DownloaderStatistics getStatistics() {
        return new DownloaderStatistics(0L, 0L);
    }

    @Override
    public List<Peer> getPeers(Torrent torrent) {
        Map<String, String> requirements = new HashMap<>();
        requirements.put("task_id", torrent.getId());
        try (Response resp = httpClient.newCall(new Request.Builder()
                .url(apiEndpoint + BCEndpoint.GET_TASK_PEERS.getEndpoint())
                .post(RequestBody.create(JsonUtil.standard().toJson(requirements), MEDIA_TYPE_JSON))
                .header("Authorization", "Bearer " + this.deviceToken)
                .build()).execute()) {
            if (resp.code() != 200) {
                throw new IllegalStateException(tlUI(Lang.DOWNLOADER_BC_FAILED_REQUEST_PEERS_LIST_IN_TORRENT, resp.code(), resp.body().string()));
            }
            var peers = JsonUtil.standard().fromJson(resp.body().string(), BCTaskPeersResponse.class);
            if (peers.getPeers() == null) {
                return Collections.emptyList();
            }
            var noGroupField = peers.getPeers().stream().noneMatch(dto -> dto.getGroup() != null);
            var stream = peers.getPeers().stream();

            if (!noGroupField) { // 对于新版本，添加一个 group 过滤
                stream = stream.filter(dto -> dto.getGroup().equals("connected"));
            }

            return stream.map(peer -> new PeerImpl(parseAddress(peer.getIp(), peer.getRemotePort(), peer.getListenPort()),
                    peer.getIp(),
                    new String(ByteUtil.hexToByteArray(peer.getPeerId()), StandardCharsets.ISO_8859_1),
                    peer.getClientType(),
                    peer.getDlRate(),
                    peer.getDlSize() != null ? peer.getDlSize() : -1,
                    peer.getUpRate(),
                    peer.getUpSize() != null ? peer.getUpSize() : -1,
                    peer.getPermillage() / 1000.0d, null)
            ).collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException(e);
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

    private void setBanListIncrement(Collection<BanMetadata> added) {
        StringJoiner joiner = new StringJoiner("\n");
        added.forEach(p -> joiner.add(p.getPeer().getAddress().getIp()));
        operateBanList("merge", joiner.toString());
    }

    protected void setBanListFull(Collection<PeerAddress> peerAddresses) {
        StringJoiner joiner = new StringJoiner("\n");
        peerAddresses.forEach(p -> joiner.add(p.getIp()));
        operateBanList("replace", joiner.toString());
    }

    private void operateBanList(String mode, String content) {
        Map<String, String> banListSettings = new HashMap<>();
        banListSettings.put("import_type", mode);
        banListSettings.put("content_base64", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
        try (Response resp = httpClient.newCall(new Request.Builder()
                .url(apiEndpoint + BCEndpoint.IP_FILTER_UPLOAD.getEndpoint())
                .post(RequestBody.create(JsonUtil.standard().toJson(banListSettings), MEDIA_TYPE_JSON))
                .header("Authorization", "Bearer " + this.deviceToken)
                .build()).execute()) {
            if (resp.code() != 200) {
                log.error(tlUI(DOWNLOADER_BC_FAILED_SAVE_BANLIST, name, apiEndpoint, resp.code(), "HTTP ERROR", resp.body().string()));
                throw new IllegalStateException("Save BitComet banlist error: statusCode=" + resp.code());
            }
        } catch (IOException e) {
            log.error(tlUI(DOWNLOADER_BC_FAILED_SAVE_BANLIST, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
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
        private boolean incrementBan;
        private String httpVersion;
        private boolean verifySsl;
        private boolean ignorePrivate;

        public static Config readFromYaml(ConfigurationSection section) {
            Config config = new Config();
            config.setType("bitcomet");
            config.setEndpoint(section.getString("endpoint"));
            if (config.getEndpoint().endsWith("/")) { // 浏览器复制党 workaround 一下， 避免连不上的情况
                config.setEndpoint(config.getEndpoint().substring(0, config.getEndpoint().length() - 1));
            }
            config.setUsername(section.getString("username", ""));
            config.setPassword(section.getString("password", ""));
            config.setIncrementBan(section.getBoolean("increment-ban", true));
            config.setHttpVersion(section.getString("http-version", "HTTP_1_1"));
            config.setVerifySsl(section.getBoolean("verify-ssl", true));
            config.setIgnorePrivate(section.getBoolean("ignore-private", false));
            return config;
        }

        public YamlConfiguration saveToYaml() {
            YamlConfiguration section = new YamlConfiguration();
            section.set("type", "bitcomet");
            section.set("endpoint", endpoint);
            section.set("username", username);
            section.set("password", password);
            section.set("increment-ban", incrementBan);
            section.set("http-version", httpVersion);
            section.set("verify-ssl", verifySsl);
            section.set("ignore-private", ignorePrivate);
            return section;
        }
    }
}
