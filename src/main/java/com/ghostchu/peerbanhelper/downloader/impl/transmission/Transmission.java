package com.ghostchu.peerbanhelper.downloader.impl.transmission;

import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.bittorrent.tracker.Tracker;
import com.ghostchu.peerbanhelper.downloader.*;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.traversal.NatAddressProvider;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.google.gson.JsonObject;
import com.vdurmont.semver4j.Semver;
import cordelia.client.TrClient;
import cordelia.client.TypedResponse;
import cordelia.rpc.*;
import cordelia.rpc.types.Fields;
import cordelia.rpc.types.Status;
import inet.ipaddr.IPAddress;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

public final class Transmission extends AbstractDownloader {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(Transmission.class);
    private final TrClient client;
    private final String blocklistUrl;
    private final Config config;

    public Transmission(String id, String blocklistUrl, Config config, AlertManager alertManager, HTTPUtil httpUtil, NatAddressProvider natAddressProvider) {
        super(id, alertManager, natAddressProvider);
        this.config = config;
        this.client = new TrClient(httpUtil, config.getEndpoint() + config.getRpcUrl(), config.getUsername(), config.getPassword(), config.isVerifySsl());
        this.blocklistUrl = blocklistUrl;
    }

    @Override
    public @NotNull String getName() {
        return config.getName();
    }

    private static String generateBlocklistUrl(String pbhServerAddress) {
        return pbhServerAddress + "/blocklist/p2p-plain-format";
    }

    public static Transmission loadFromConfig(String id, String pbhServerAddress, ConfigurationSection section, AlertManager alertManager, HTTPUtil httpUtil, NatAddressProvider natAddressProvider) {
        Config config = Config.readFromYaml(section, id);
        return new Transmission(id, generateBlocklistUrl(pbhServerAddress), config, alertManager, httpUtil, natAddressProvider);
    }

    public static Transmission loadFromConfig(String id, String pbhServerAddress, JsonObject section, AlertManager alertManager, HTTPUtil httpUtil, NatAddressProvider natAddressProvider) {
        Transmission.Config config = JsonUtil.getGson().fromJson(section.toString(), Transmission.Config.class);
        return new Transmission(id, generateBlocklistUrl(pbhServerAddress), config, alertManager, httpUtil, natAddressProvider);
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
    public @NotNull String getType() {
        return "Transmission";
    }

    @Override
    public boolean isPaused() {
        return config.isPaused();
    }

    @Override
    public @NotNull List<DownloaderFeatureFlag> getFeatureFlags() {
        return List.of(DownloaderFeatureFlag.UNBAN_IP, DownloaderFeatureFlag.TRAFFIC_STATS ,DownloaderFeatureFlag.LIVE_UPDATE_BT_PROTOCOL_PORT);
    }

    @Override
    public void setPaused(boolean paused) {
        super.setPaused(paused);
        config.setPaused(paused);
    }

    @Override
    public DownloaderLoginResult login0() throws IOException {
        RqSessionGet get = new RqSessionGet();
        TypedResponse<RsSessionGet> resp = client.execute(get); // 执行任意 RPC 操作以刷新 session
        String version = resp.getArgs().getVersion();
        if (version.length() > 5) {
            version = version.substring(0, 5);
        }
        Semver semver = new Semver(version, Semver.SemverType.LOOSE);
        // must 4.1.0 or higher
        if (semver.getMajor() < 4) {
            return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_TR_KNOWN_INCOMPATIBILITY, "Transmission version must be 4.1.0 or higher"));
        }
        if (semver.getMajor() == 4) {
            if (semver.getMinor() < 1) {
                return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_TR_KNOWN_INCOMPATIBILITY, "Transmission version must higher then (or equals to) 4.1.0"));
            }
        }
        if (!resp.getArgs().getBlocklistEnabled() || !resp.getArgs().getBlocklistUrl().startsWith(blocklistUrl)) {
            if (!setBlockListUrl(blocklistUrl)) {
                return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_TR_INCORRECT_BANLIST_API_RESP, "Unable to set Transmission blocklist"));
            }
            if (!updateBlockList()) {
                return new DownloaderLoginResult(DownloaderLoginResult.Status.REQUIRE_TAKE_ACTIONS, new TranslationComponent(Lang.DOWNLOADER_TRANSMISSION_BLOCKLIST_UPDATE_FAILED, getName()));
            }
        }
        return new DownloaderLoginResult(DownloaderLoginResult.Status.SUCCESS, new TranslationComponent(Lang.STATUS_TEXT_OK));
    }

    private boolean setBlockListUrl(String blocklistUrl) {
        RqSessionSet set = RqSessionSet.builder()
                .blocklistUrl(blocklistUrl + "?t=" + System.currentTimeMillis()) // 更改 URL 来确保更改生效
                .blocklistEnabled(true)
                .build();
        TypedResponse<RsSessionGet> sessionSetResp = client.execute(set);
        return sessionSetResp.isSuccess();
    }

    private boolean updateBlockList() {
        RqBlockList updateBlockList = new RqBlockList();
        TypedResponse<RsBlockList> updateBlockListResp = client.execute(updateBlockList);
        if (!updateBlockListResp.isSuccess()) {
            setBlockListUrl("http://peerbanhelper-blocklist-update-failed.com/check-peerbanhelper-webui-prefix-settings");
            return false;
        }
        return true;
    }

    @Override
    public @NotNull List<Torrent> getTorrents() {
        return fetchTorrents(true, !config.isIgnorePrivate());
    }

    @Override
    public @NotNull List<Torrent> getAllTorrents() {
        return fetchTorrents(false, true);
    }

    public List<Torrent> fetchTorrents(boolean onlyActiveTorrent, boolean includePrivate) {
        RqTorrentGet torrent = new RqTorrentGet(Fields.ID, Fields.HASH_STRING, Fields.NAME, Fields.PEERS_CONNECTED, Fields.STATUS, Fields.TOTAL_SIZE, Fields.PEERS, Fields.RATE_DOWNLOAD, Fields.RATE_UPLOAD, Fields.PEER_LIMIT, Fields.PERCENT_DONE, Fields.SIZE_WHEN_DONE, Fields.TRACKER_LIST, Fields.TRACKER_STATS, Fields.IS_PRIVATE);
        TypedResponse<RsTorrentGet> rsp = client.execute(torrent);
        return rsp.getArgs().getTorrents().stream()
                .filter(t -> {
                    if (onlyActiveTorrent) {
                        return t.getStatus() == Status.DOWNLOADING || t.getStatus() == Status.SEEDING;
                    }
                    return true;
                })
                .filter(t -> includePrivate || !t.getIsPrivate())
                .map(backend -> new TRTorrent(backend, this::natTranslate)).collect(Collectors.toList());
    }

    @Override
    public @NotNull List<Peer> getPeers(@NotNull Torrent torrent) {
        TRTorrent trTorrent = (TRTorrent) torrent;
        return trTorrent.getPeers();
    }

    @Override
    public @NotNull List<Tracker> getTrackers(@NotNull Torrent torrent) {
        TRTorrent trTorrent = (TRTorrent) torrent;
        return trTorrent.getTrackers();
    }

    @Override
    public void setTrackers(@NotNull Torrent torrent, @NotNull List<Tracker> trackers) {
        StringJoiner trackersJoiner = new StringJoiner("\n\n"); // 空一行
        trackers.forEach(t -> trackersJoiner.add(t.toString()));
        RqTorrentSet set = RqTorrentSet.builder()
                .ids(List.of(torrent.getId()))
                .trackerList(trackersJoiner.toString())
                .build();
        client.execute(set);
    }

    @SneakyThrows
    @Override
    public void setBanList(@NotNull Collection<IPAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed, boolean applyFullList) {
        RqBlockList updateBlockList = new RqBlockList();
        TypedResponse<RsBlockList> updateBlockListResp = client.execute(updateBlockList);
        if (!updateBlockListResp.isSuccess()) {
            log.error(tlUI(Lang.DOWNLOADER_TR_INCORRECT_SET_BANLIST_API_RESP));
            alertManager.publishAlert(true, AlertLevel.WARN,
                    "downloader-" + getEndpoint() + "-transmission-blocklist-update-error",
                    new TranslationComponent(Lang.ALERT_DOWNLOADER_TRANSMISSION_BLOCKLIST_UPDATE_FAILED_TITLE, getName()),
                    new TranslationComponent(Lang.ALERT_DOWNLOADER_TRANSMISSION_BLOCKLIST_UPDATE_FAILED_DESCRIPTION, getName(), blocklistUrl)
            );
        } else {
            log.info(tlUI(Lang.DOWNLOADER_TR_UPDATED_BLOCKLIST), updateBlockListResp.getArgs().getBlockListSize());
        }
    }

    @Override
    public @NotNull DownloaderStatistics getStatistics() {
        RqSessionStats sessionStats = new RqSessionStats();
        TypedResponse<RsSessionStats> sessionStatsResp = client.execute(sessionStats);
        var stats = sessionStatsResp.getArgs();
        return new DownloaderStatistics(stats.getCumulativeStats().getUploadedBytes(), stats.getCumulativeStats().getDownloadedBytes());
    }

//    /**
//     * 添加标签到指定种子
//     *
//     * @param torrent Torrent
//     * @param tag     标签
//     */
//    @Override
//    public void addTag(Torrent torrent, String tag) {
//
//    }
//
//    /**
//     * 从种子上移除指定的标签
//     *
//     * @param torrent Torrent
//     * @param tag     标签
//     */
//    @Override
//    public void removeTag(Torrent torrent, String tag) {
//
//    }
//
//    /**
//     * 以纯文本方式获取指定 Torrent 上的所有标签
//     *
//     * @param torrent Torrent
//     * @return 标签列表
//     */
//    @Override
//    public List<String> getTags(Torrent torrent) {
//        return Collections.emptyList();
//    }
//
//    /**
//     * 暂停 Torrent
//     *
//     * @param torrent Torrent
//     */
//    @Override
//    public void pauseTorrent(Torrent torrent) {
//        RqTorrentStop start = new RqTorrentStop(List.of(Long.parseLong(torrent.getId())));
//        TypedResponse<RsEmpty> sessionSetResp = client.execute(start);
//        if (!sessionSetResp.isSuccess()) {
//            log.error(tlUI(Lang.DOWNLOADER_FAILED_STOP_TORRENT, getName(), sessionSetResp.getResult()));
//        }
//    }
//
//    /**
//     * 开始 Torrent
//     *
//     * @param torrent Torrent
//     */
//    @Override
//    public void startTorrent(Torrent torrent) {
//        RqTorrentStart start = new RqTorrentStart(List.of(Long.parseLong(torrent.getId())));
//        TypedResponse<RsEmpty> sessionSetResp = client.execute(start);
//        if (!sessionSetResp.isSuccess()) {
//            log.error(tlUI(Lang.DOWNLOADER_FAILED_START_TORRENT, getName(), sessionSetResp.getResult()));
//        }
//    }

    /**
     * 获取当前下载器的限速配置
     *
     * @return 限速配置，如果不支持或者请求错误，则可能返回 null
     */
    @Override
    public @Nullable DownloaderSpeedLimiter getSpeedLimiter() {
        RqSessionGet sessionGet = new RqSessionGet(List.of(Fields.DOWNLOAD_LIMIT, Fields.DOWNLOAD_LIMITED, Fields.UPLOAD_LIMIT, Fields.UPLOAD_LIMITED));
        TypedResponse<RsSessionGet> sessionGetResp = client.execute(sessionGet);
        if (sessionGetResp.isSuccess()) {
            RsSessionGet args = sessionGetResp.getArgs();
            long downloadLimit = args.getSpeedLimitDown() * 1024;
            long uploadLimit = args.getSpeedLimitUp() * 1024;
            if (!args.getSpeedLimitDownEnabled()) {
                downloadLimit = 0;
            }
            if (!args.getSpeedLimitUpEnabled()) {
                uploadLimit = 0;
            }
            return new DownloaderSpeedLimiter(uploadLimit, downloadLimit);
        }
        log.error(tlUI(Lang.DOWNLOADER_FAILED_RETRIEVE_SPEED_LIMITER, getName(), sessionGetResp.getResult()));
        return null;
    }

    /**
     * 设置当前下载器的限速配置
     *
     * @param speedLimiter 限速配置
     */
    @Override
    public void setSpeedLimiter(@NotNull DownloaderSpeedLimiter speedLimiter) {
        RqSessionSet set = RqSessionSet.builder()
                .altSpeedEnabled(false)
                .altSpeedTimeEnabled(false)
                .speedLimitDown(speedLimiter.isDownloadUnlimited() ? (int) (Math.max(1024L, speedLimiter.download()) / 1024) : (int) (speedLimiter.download() / 1024))
                .speedLimitUp(speedLimiter.isUploadUnlimited() ? (int) (Math.max(1024L, speedLimiter.upload()) / 1024) : (int) (speedLimiter.upload() / 1024))
                .speedLimitUpEnabled(!speedLimiter.isUploadUnlimited())
                .speedLimitDownEnabled(!speedLimiter.isDownloadUnlimited())
                .build();
        TypedResponse<RsSessionGet> sessionSetResp = client.execute(set);
        if (!sessionSetResp.isSuccess()) {
            log.error(tlUI(Lang.DOWNLOADER_FAILED_SET_SPEED_LIMITER, getName(), sessionSetResp.getResult()));
        }
    }

    @Override
    public int getBTProtocolPort() {
        RqSessionGet sessionGet = new RqSessionGet(List.of(Fields.PEER_PORT));
        TypedResponse<RsSessionGet> sessionGetResp = client.execute(sessionGet);
        if (sessionGetResp.isSuccess()) {
            RsSessionGet args = sessionGetResp.getArgs();
            return args.getPeerPort();
        }
        log.error(tlUI(Lang.DOWNLOADER_FAILED_RETRIEVE_BT_PROTOCOL_PORT, getName(), sessionGetResp.getResult()));
        return -1;
    }

    @Override
    public void setBTProtocolPort(int port) {
        RqSessionSet set = RqSessionSet.builder()
                .peerPortRandomOnStart(false)
                .peerPort(port)
                .build();
        TypedResponse<RsSessionGet> sessionSetResp = client.execute(set);
        if (!sessionSetResp.isSuccess()) {
            log.error(tlUI(Lang.DOWNLOADER_FAILED_SAVE_BT_PROTOCOL_PORT, getName(), sessionSetResp.getResult()));
        }
    }

    @Override
    public void runScheduleTasks() {
        // unpauseTorrents(false);
    }

    @Override
    public void close() {
        //  unpauseTorrents(true);
        client.shutdown();
    }

    @NoArgsConstructor
    @Data
    public static class Config {
        private String name;
        private String type;
        private String endpoint;
        private String username;
        private String password;
        private boolean verifySsl;
        private String rpcUrl;
        private boolean ignorePrivate;
        private boolean paused;

        public static Transmission.Config readFromYaml(ConfigurationSection section, String alternativeName) {
            Transmission.Config config = new Transmission.Config();
            config.setType("transmission");
            config.setName(section.getString("name", alternativeName));
            config.setEndpoint(section.getString("endpoint"));
            if (config.getEndpoint().endsWith("/")) { // 浏览器复制党 workaround 一下， 避免连不上的情况
                config.setEndpoint(config.getEndpoint().substring(0, config.getEndpoint().length() - 1));
            }
            config.setUsername(section.getString("username", ""));
            config.setPassword(section.getString("password", ""));
            config.setRpcUrl(section.getString("rpc-url", "/transmission/rpc"));
            config.setVerifySsl(section.getBoolean("verify-ssl", true));
            config.setIgnorePrivate(section.getBoolean("ignore-private", false));
            config.setPaused(section.getBoolean("paused", false));
            return config;
        }

        public YamlConfiguration saveToYaml() {
            YamlConfiguration section = new YamlConfiguration();
            section.set("type", "transmission");
            section.set("name", name);
            section.set("endpoint", endpoint);
            section.set("username", username);
            section.set("password", password);
            section.set("rpc-url", rpcUrl);
            section.set("verify-ssl", verifySsl);
            section.set("ignore-private", ignorePrivate);
            section.set("paused", paused);
            return section;
        }
    }
}
