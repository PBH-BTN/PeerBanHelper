package com.ghostchu.peerbanhelper.downloader.impl.bitcomet;

import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.downloader.AbstractDownloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderFeatureFlag;
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
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.common.net.HostAndPort;
import com.google.gson.JsonObject;
import com.vdurmont.semver4j.Semver;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import static com.ghostchu.peerbanhelper.text.Lang.DOWNLOADER_BC_FAILED_SAVE_BANLIST;
import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

public class BitComet extends AbstractDownloader {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(BitComet.class);
    private static final UUID clientId = UUID.nameUUIDFromBytes("PeerBanHelper".getBytes(StandardCharsets.UTF_8));
    protected final String apiEndpoint;
    protected final HttpClient httpClient;
    private final Config config;
    private String deviceToken;
    private String serverId;
    private Semver serverVersion;
    private String serverName;

    public BitComet(String name, Config config, AlertManager alertManager) {
        super(name, alertManager);
        BCAESTool.init();
        this.config = config;
        this.apiEndpoint = config.getEndpoint();
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        Methanol.Builder builder = Methanol.newBuilder()
                .version(HttpClient.Version.valueOf(config.getHttpVersion()))
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Client-Type", "BitComet WebUI")
                .defaultHeader("User-Agent", "PeerBanHelper BitComet Adapter")
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.of(15, ChronoUnit.SECONDS))
                .headersTimeout(Duration.of(15, ChronoUnit.SECONDS))
                .readTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .requestTimeout(Duration.of(30, ChronoUnit.SECONDS));
        if (!config.isVerifySsl() && HTTPUtil.getIgnoreSslContext() != null) {
            builder.sslContext(HTTPUtil.getIgnoreSslContext());
        }
        this.httpClient = builder.build();
    }

    public static BitComet loadFromConfig(String name, ConfigurationSection section, AlertManager alertManager) {
        Config config = Config.readFromYaml(section);
        return new BitComet(name, config, alertManager);
    }

    public static BitComet loadFromConfig(String name, JsonObject section, AlertManager alertManager) {
        Config config = JsonUtil.getGson().fromJson(section, Config.class);
        return new BitComet(name, config, alertManager);
    }

    private static PeerAddress parseAddress(String address, int port, int listenPort) {
        address = address.trim();
        HostAndPort hostAndPort = HostAndPort.fromString(address);
        return new PeerAddress(hostAndPort.getHost(), hostAndPort.getPortOrDefault(port));
    }

    @Override
    public List<DownloaderFeatureFlag> getFeatureFlags() {
        List<DownloaderFeatureFlag> flags = new ArrayList<>(1);
        if (is211Newer()) {
            flags.add(DownloaderFeatureFlag.UNBAN_IP);
        }
        return flags;
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
        if (isLoggedIn())
            return new DownloaderLoginResult(DownloaderLoginResult.Status.SUCCESS, new TranslationComponent(Lang.STATUS_TEXT_OK)); // 重用 Session 会话
        Map<String, String> loginAttemptCred = new HashMap<>();
        loginAttemptCred.put("username", config.getUsername());
        loginAttemptCred.put("password", config.getPassword());
        String aesEncrypted = BCAESTool.credential(JsonUtil.standard().toJson(loginAttemptCred), clientId.toString());
        Map<String, String> loginJsonObject = new HashMap<>();
        loginJsonObject.put("authentication", aesEncrypted);
        loginJsonObject.put("client_id", clientId.toString());
        try {
            HttpResponse<String> request = httpClient.send(
                    MutableRequest.POST(apiEndpoint + BCEndpoint.USER_LOGIN.getEndpoint(),
                            HttpRequest.BodyPublishers.ofString(JsonUtil.standard().toJson(loginJsonObject)))
                    , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            var loginResponse = JsonUtil.standard().fromJson(request.body(), BCLoginResponse.class);
            if (loginResponse.getErrorCode().equalsIgnoreCase("PASSWORD_ERROR")) {
                return new DownloaderLoginResult(DownloaderLoginResult.Status.INCORRECT_CREDENTIAL, new TranslationComponent(Lang.DOWNLOADER_LOGIN_EXCEPTION, request.body()));
            }
            if (!loginResponse.getErrorCode().equalsIgnoreCase("ok")) {
                return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_LOGIN_EXCEPTION, request.body()));
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
                return new DownloaderLoginResult(DownloaderLoginResult.Status.MISSING_COMPONENTS, new TranslationComponent(Lang.DOWNLOADER_BC_VERSION_UNACCEPTABLE, request.body()));
            }
            // 版本检查结束
            Map<String, String> inviteTokenRetrievePayload = new HashMap<>();
            inviteTokenRetrievePayload.put("device_id", clientId.toString());
            inviteTokenRetrievePayload.put("device_name", "PeerBanHelper - BitComet Adapter");
            inviteTokenRetrievePayload.put("invite_token", loginResponse.getInviteToken());
            inviteTokenRetrievePayload.put("platform", "webui");
            HttpResponse<String> retrieveDeviceToken = httpClient.send(
                    MutableRequest.POST(apiEndpoint + BCEndpoint.GET_DEVICE_TOKEN.getEndpoint(),
                                    HttpRequest.BodyPublishers.ofString(JsonUtil.standard().toJson(inviteTokenRetrievePayload)))
                            .header("Authorization", "Bearer " + loginResponse.getInviteToken()),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            var deviceTokenResponse = JsonUtil.standard().fromJson(retrieveDeviceToken.body(), BCDeviceTokenResult.class);
            this.deviceToken = deviceTokenResponse.getDeviceToken();
            this.serverId = deviceTokenResponse.getServerId();
            this.serverVersion = new Semver(deviceTokenResponse.getVersion(), Semver.SemverType.LOOSE);
            this.serverName = deviceTokenResponse.getServerName();
            if (queryNeedReConfigureIpFilter()) {
                enableIpFilter();
            }
            return new DownloaderLoginResult(DownloaderLoginResult.Status.SUCCESS, new TranslationComponent(Lang.STATUS_TEXT_OK));
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
        return "BitComet";
    }

    public boolean isLoggedIn() {
        try {
            queryNeedReConfigureIpFilter();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean queryNeedReConfigureIpFilter() throws IOException, InterruptedException {
        HttpResponse<String> query =
                httpClient.send(
                        MutableRequest.POST(apiEndpoint + BCEndpoint.GET_IP_FILTER_CONFIG.getEndpoint(),
                                        HttpRequest.BodyPublishers.ofString("{}"))
                                .header("Authorization", "Bearer " + this.deviceToken),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );
        if (query.statusCode() != 200) {
            throw new IllegalStateException("Not a excepted statusCode while query the IPFilter status");
        }
        var resp = JsonUtil.standard().fromJson(query.body(), BCIpFilterResponse.class);
        boolean isBlacklistMode = false;
        if (resp.getIpFilterConfig().getEnableWhitelistMode() != null) { // 2.10
            isBlacklistMode = !resp.getIpFilterConfig().getEnableWhitelistMode();
        }
        if (resp.getIpFilterConfig().getFilterMode() != null) { // 2.11
            isBlacklistMode = "blacklist".equals(resp.getIpFilterConfig().getFilterMode());
        }
        return !resp.getIpFilterConfig().getEnableIpFilter() || !isBlacklistMode;
    }

    private void enableIpFilter() throws IOException, InterruptedException {
        Map<String, Object> settings = new HashMap<>() {{
            put("ip_filter_config", new HashMap<>() {{
                put("enable_ipfilter", true);
                put("enable_ip_filter", true);
                put("enable_whitelist_mode", false); // 2.10
                put("ipfilter_mode", "blacklist"); // 2.11
            }});
        }};
        HttpResponse<String> updatePreferencesToEnableIpFilter =
                httpClient.send(
                        MutableRequest.POST(apiEndpoint + BCEndpoint.SET_IP_FILTER_CONFIG.getEndpoint(),
                                        HttpRequest.BodyPublishers.ofString(JsonUtil.standard().toJson(settings)))
                                .header("Authorization", "Bearer " + this.deviceToken),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );
        var configResp = JsonUtil.standard().fromJson(updatePreferencesToEnableIpFilter.body(), BCConfigSetResponse.class);
        if ("ok".equalsIgnoreCase(configResp.getErrorCode())) {
            log.info(tlUI(Lang.DOWNLOADER_BC_CONFIG_IP_FILTER_SUCCESS));
        } else {
            log.error(tlUI(Lang.DOWNLOADER_BC_CONFIG_IP_FILTER_FAILED));
        }
    }

    @Override
    public List<Torrent> getTorrents() {
        Map<String, String> requirements = new HashMap<>();
        requirements.put("group_state", "ACTIVE");
        requirements.put("sort_key", "");
        requirements.put("sort_order", "unsorted");
        HttpResponse<String> request;
        try {
            request = httpClient.send(
                    MutableRequest.POST(apiEndpoint + BCEndpoint.GET_TASK_LIST.getEndpoint(),
                                    HttpRequest.BodyPublishers.ofString(JsonUtil.standard().toJson(requirements)))
                            .header("Authorization", "Bearer " + this.deviceToken)
                    , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (request.statusCode() != 200) {
            throw new IllegalStateException(tlUI(Lang.DOWNLOADER_BC_FAILED_REQUEST_TORRENT_LIST, request.statusCode(), request.body()));
        }
        var response = JsonUtil.standard().fromJson(request.body(), BCTaskListResponse.class);

        Semaphore semaphore = new Semaphore(4);
        List<BCTaskTorrentResponse> torrentResponses = Collections.synchronizedList(new ArrayList<>(response.getTasks().size()));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            response.getTasks().stream().filter(t -> t.getType().equals("BT"))
                    .forEach(torrent -> executor.submit(() -> {
                        try {
                            semaphore.acquire();
                            Map<String, String> taskIds = new HashMap<>();
                            taskIds.put("task_id", String.valueOf(torrent.getTaskId()));
                            HttpResponse<String> fetch = httpClient.send(MutableRequest.POST(apiEndpoint + BCEndpoint.GET_TASK_SUMMARY.getEndpoint(),
                                                    HttpRequest.BodyPublishers.ofString(JsonUtil.standard().toJson(taskIds)))
                                            .header("Authorization", "Bearer " + this.deviceToken),
                                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                            var torrentResp = JsonUtil.standard().fromJson(fetch.body(), BCTaskTorrentResponse.class);
                            torrentResponses.add(torrentResp);
                        } catch (IOException | InterruptedException e) {
                            log.warn(tlUI(Lang.DOWNLOADER_BITCOMET_UNABLE_FETCH_TASK_SUMMARY), e);
                        } finally {
                            semaphore.release();
                        }
                    }));
        }
        return torrentResponses.stream().map(torrent -> new TorrentImpl(Long.toString(torrent.getTask().getTaskId()),
                torrent.getTask().getTaskName(),
                torrent.getTaskDetail().getInfohash() != null ? torrent.getTaskDetail().getInfohash() : torrent.getTaskDetail().getInfohashV2(),
                torrent.getTaskDetail().getTotalSize(),
                torrent.getTaskStatus().getDownloadPermillage() / 1000.0d,
                torrent.getTask().getUploadRate(),
                torrent.getTask().getDownloadRate(),
                torrent.getTaskDetail().getTorrentPrivate()
        )).collect(Collectors.toList());
    }

    @Override
    public DownloaderStatistics getStatistics() {
        return new DownloaderStatistics(0L, 0L);
    }

    @Override
    public List<Peer> getPeers(Torrent torrent) {
        HttpResponse<InputStream> resp;
        try {
            Map<String, Object> requirements = new HashMap<>();
            requirements.put("groups", List.of("peers_connected")); // 2.11 Beta 3 可以限制获取哪一类 Peers，注意下面仍需要检查，因为旧版本不支持
            requirements.put("task_id", torrent.getId());
            requirements.put("max_count", String.valueOf(Integer.MAX_VALUE)); // 获取全量列表，因为我们需要检查所有 Peers
            resp = httpClient.send(MutableRequest.POST(apiEndpoint + BCEndpoint.GET_TASK_PEERS.getEndpoint(),
                                    HttpRequest.BodyPublishers.ofString(JsonUtil.standard().toJson(requirements)))
                            .header("Authorization", "Bearer " + this.deviceToken),
                    HttpResponse.BodyHandlers.ofInputStream());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (resp.statusCode() != 200) {
            throw new IllegalStateException(tlUI(Lang.DOWNLOADER_BC_FAILED_REQUEST_PEERS_LIST_IN_TORRENT, resp.statusCode(), resp.body()));
        }
        try (InputStreamReader reader = new InputStreamReader(resp.body())) {
            var peers = JsonUtil.standard().fromJson(reader, BCTaskPeersResponse.class);
            if (peers.getPeers() == null) {
                return Collections.emptyList();
            }
            var noGroupField = peers.getPeers().stream().noneMatch(dto -> dto.getGroup() != null); // 2.10 的一些版本没有 group 字段
            var stream = peers.getPeers().stream();

            if (!noGroupField) { // 对于新版本，添加一个 group 过滤
                stream = stream.filter(dto -> dto.getGroup().equals("connected") // 2.10 正式版
                                              || dto.getGroup().equals("connected_peers") // 2.11 Beta 1-2
                                              || dto.getGroup().equals("peers_connected")); // 2.11 Beta 3
            }
            return stream.map(peer -> new PeerImpl(parseAddress(peer.getIp(), peer.getRemotePort(), peer.getListenPort()),
                    peer.getIp(),
                    new String(ByteUtil.hexToByteArray(peer.getPeerId()), StandardCharsets.ISO_8859_1),
                    peer.getClientType(),
                    peer.getDlRate(),
                    peer.getDlSize() != null ? peer.getDlSize() : -1, // 兼容 2.10
                    peer.getUpRate(),
                    peer.getUpSize() != null ? peer.getUpSize() : -1, // 兼容 2.10
                    peer.getPermillage() / 1000.0d, null, Collections.emptyList())
            ).collect(Collectors.toList());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void setBanList(@NotNull Collection<PeerAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed, boolean applyFullList) {
        if (removed != null && removed.isEmpty() && added != null && config.isIncrementBan() && !applyFullList && !is211Newer()) {
            setBanListIncrement(added);
        } else {
            if (removed != null && !removed.isEmpty()) {
                unbanPeers(removed.stream().map(meta -> meta.getPeer().getAddress().toString()).toList());
            }
            setBanListFull(fullList);
        }
    }

    private void setBanListIncrement(Collection<BanMetadata> added) {
        StringJoiner joiner = new StringJoiner("\n");
        added.forEach(p -> joiner.add(p.getPeer().getAddress().getIp()));
        operateBanListLegacy("merge", joiner.toString());
    }

    protected void setBanListFull(Collection<PeerAddress> peerAddresses) {
        StringJoiner joiner = new StringJoiner("\n");
        peerAddresses.forEach(p -> joiner.add(p.getIp()));
        operateBanListLegacy("replace", joiner.toString());
    }

    private boolean is211Newer() {
        return serverVersion.getMajor() >= 2 && serverVersion.getMinor() != null && serverVersion.getMinor() >= 11;
    }

    private void unbanPeers(List<String> peerAddresses) {
        Map<String, Object> banListSettings = new HashMap<>();
        banListSettings.put("ip_list", peerAddresses);
        banListSettings.put("unban_range", "unban_peers_in_all_tasks");
        try {
            HttpResponse<String> request = httpClient.send(MutableRequest.POST(apiEndpoint + BCEndpoint.TASK_UNBAN_PEERS.getEndpoint(),
                                    HttpRequest.BodyPublishers.ofString(JsonUtil.standard().toJson(banListSettings)))
                            .header("Authorization", "Bearer " + this.deviceToken),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (request.statusCode() != 200) {
                log.error(tlUI(DOWNLOADER_BC_FAILED_SAVE_BANLIST, name, apiEndpoint, request.statusCode(), "HTTP ERROR (unban_peers)", request.body()));
                throw new IllegalStateException("Save BitComet banlist error: statusCode=" + request.statusCode());
            }
        } catch (Exception e) {
            log.error(tlUI(DOWNLOADER_BC_FAILED_SAVE_BANLIST, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
            throw new IllegalStateException(e);
        }
    }

    private void operateBanListLegacy(String mode, String content) {
        Map<String, String> banListSettings = new HashMap<>();
        banListSettings.put("import_type", mode);
        banListSettings.put("data_type", "data_file");
        banListSettings.put("content_base64", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
        try {
            HttpResponse<String> request = httpClient.send(MutableRequest.POST(apiEndpoint + BCEndpoint.IP_FILTER_UPLOAD.getEndpoint(),
                                    HttpRequest.BodyPublishers.ofString(JsonUtil.standard().toJson(banListSettings)))
                            .header("Authorization", "Bearer " + this.deviceToken),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (request.statusCode() != 200) {
                log.error(tlUI(DOWNLOADER_BC_FAILED_SAVE_BANLIST, name, apiEndpoint, request.statusCode(), "HTTP ERROR", request.body()));
                throw new IllegalStateException("Save BitComet banlist error: statusCode=" + request.statusCode());
            }
        } catch (Exception e) {
            log.error(tlUI(DOWNLOADER_BC_FAILED_SAVE_BANLIST, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
            throw new IllegalStateException(e);
        }
    }

//    private void operateBanListNew(String mode, String content) {
//        Map<String, String> banListSettings = new HashMap<>();
//        banListSettings.put("data_type", "manual_list");
//        banListSettings.put("content_base64", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
//        try {
//            HttpResponse<String> request = httpClient.send(MutableRequest.POST(apiEndpoint + BCEndpoint.IP_FILTER_UPLOAD.getEndpoint(),
//                                    HttpRequest.BodyPublishers.ofString(JsonUtil.standard().toJson(banListSettings)))
//                            .header("Authorization", "Bearer " + this.deviceToken),
//                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
//            if (request.statusCode() != 200) {
//                log.error(tlUI(DOWNLOADER_BC_FAILED_SAVE_BANLIST, name, apiEndpoint, request.statusCode(), "HTTP ERROR", request.body()));
//                throw new IllegalStateException("Save BitComet banlist error: statusCode=" + request.statusCode());
//            }
//        } catch (Exception e) {
//            log.error(tlUI(DOWNLOADER_BC_FAILED_SAVE_BANLIST, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
//            throw new IllegalStateException(e);
//        }
//    }

    @Override
    public void close() throws Exception {

    }

    @Override
    public int getMaxConcurrentPeerRequestSlots() {
        return 4;
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
