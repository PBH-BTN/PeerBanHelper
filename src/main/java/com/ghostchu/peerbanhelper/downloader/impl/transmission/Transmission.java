package com.ghostchu.peerbanhelper.downloader.impl.transmission;

import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.downloader.AbstractDownloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLoginResult;
import com.ghostchu.peerbanhelper.downloader.DownloaderStatistics;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.torrent.Tracker;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
import com.google.gson.JsonObject;
import com.vdurmont.semver4j.Semver;
import cordelia.client.TrClient;
import cordelia.client.TypedResponse;
import cordelia.rpc.*;
import cordelia.rpc.types.Fields;
import cordelia.rpc.types.Status;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.net.http.HttpClient;
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

    public Transmission(String name, String blocklistUrl, Config config, AlertManager alertManager) {
        super(name, alertManager);
        this.config = config;
        this.client = new TrClient(config.getEndpoint() + config.getRpcUrl(), config.getUsername(), config.getPassword(), config.isVerifySsl(), HttpClient.Version.valueOf(config.getHttpVersion()));
        this.blocklistUrl = blocklistUrl;
        log.warn(tlUI(Lang.DOWNLOADER_TR_MOTD_WARNING));
    }

    private static String generateBlocklistUrl(String pbhServerAddress) {
        return pbhServerAddress + "/blocklist/p2p-plain-format";
    }

    public static Transmission loadFromConfig(String name, String pbhServerAddress, ConfigurationSection section, AlertManager alertManager) {
        Config config = Config.readFromYaml(section);
        return new Transmission(name, generateBlocklistUrl(pbhServerAddress), config, alertManager);
    }

    public static Transmission loadFromConfig(String name, String pbhServerAddress, JsonObject section, AlertManager alertManager) {
        Transmission.Config config = JsonUtil.getGson().fromJson(section.toString(), Transmission.Config.class);
        return new Transmission(name, generateBlocklistUrl(pbhServerAddress), config, alertManager);
    }

    @Override
    public JsonObject saveDownloaderJson() {
        return JsonUtil.getGson().toJsonTree(config).getAsJsonObject();
    }

    @Override
    public YamlConfiguration saveDownloader() {
        return config.saveToYaml();
    }

    @Override
    public String getEndpoint() {
        return config.getEndpoint();
    }

    @Override
    public String getType() {
        return "Transmission";
    }

    @Override
    public boolean isPaused() {
        return config.isPaused();
    }

    @Override
    public void setPaused(boolean paused) {
        super.setPaused(paused);
        config.setPaused(paused);
    }

    @Override
    public DownloaderLoginResult login0() {
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
    public List<Torrent> getTorrents() {
        return fetchTorrents(true, !config.isIgnorePrivate());
    }

    @Override
    public List<Torrent> getAllTorrents() {
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
                .map(TRTorrent::new).collect(Collectors.toList());
    }

    @Override
    public List<Peer> getPeers(Torrent torrent) {
        TRTorrent trTorrent = (TRTorrent) torrent;
        return trTorrent.getPeers();
    }

    @Override
    public List<Tracker> getTrackers(Torrent torrent) {
        TRTorrent trTorrent = (TRTorrent) torrent;
        return trTorrent.getTrackers();
    }

    @Override
    public void setTrackers(Torrent torrent, List<Tracker> trackers) {
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
    public void setBanList(Collection<PeerAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed, boolean applyFullList) {
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
    public DownloaderStatistics getStatistics() {
        RqSessionStats sessionStats = new RqSessionStats();
        TypedResponse<RsSessionStats> sessionStatsResp = client.execute(sessionStats);
        var stats = sessionStatsResp.getArgs();
        return new DownloaderStatistics(stats.getCumulativeStats().getUploadedBytes(), stats.getCumulativeStats().getDownloadedBytes());
    }

    @Override
    public void relaunchTorrentIfNeeded(Collection<Torrent> torrents) {

    }


    @Override
    public void relaunchTorrentIfNeededByTorrentWrapper(Collection<TorrentWrapper> torrents) {

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

        private String type;
        private String endpoint;
        private String username;
        private String password;
        private String httpVersion;
        private boolean verifySsl;
        private String rpcUrl;
        private boolean ignorePrivate;
        private boolean paused;

        public static Transmission.Config readFromYaml(ConfigurationSection section) {
            Transmission.Config config = new Transmission.Config();
            config.setType("transmission");
            config.setEndpoint(section.getString("endpoint"));
            if (config.getEndpoint().endsWith("/")) { // 浏览器复制党 workaround 一下， 避免连不上的情况
                config.setEndpoint(config.getEndpoint().substring(0, config.getEndpoint().length() - 1));
            }
            config.setUsername(section.getString("username", ""));
            config.setPassword(section.getString("password", ""));
            config.setRpcUrl(section.getString("rpc-url", "/transmission/rpc"));
            config.setHttpVersion(section.getString("http-version", "HTTP_1_1"));
            config.setVerifySsl(section.getBoolean("verify-ssl", true));
            config.setIgnorePrivate(section.getBoolean("ignore-private", false));
            config.setPaused(section.getBoolean("paused", false));
            return config;
        }

        public YamlConfiguration saveToYaml() {
            YamlConfiguration section = new YamlConfiguration();
            section.set("type", "transmission");
            section.set("endpoint", endpoint);
            section.set("username", username);
            section.set("password", password);
            section.set("rpc-url", rpcUrl);
            section.set("http-version", httpVersion);
            section.set("verify-ssl", verifySsl);
            section.set("ignore-private", ignorePrivate);
            section.set("paused", paused);
            return section;
        }
    }
}
