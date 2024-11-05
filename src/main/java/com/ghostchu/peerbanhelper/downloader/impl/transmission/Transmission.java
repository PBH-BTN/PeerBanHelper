package com.ghostchu.peerbanhelper.downloader.impl.transmission;

import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.downloader.AbstractDownloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLoginResult;
import com.ghostchu.peerbanhelper.downloader.DownloaderStatistics;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
import com.google.common.primitives.Ints;
import com.google.gson.JsonObject;
import cordelia.client.TrClient;
import cordelia.client.TypedResponse;
import cordelia.rpc.*;
import cordelia.rpc.types.Fields;
import cordelia.rpc.types.Status;
import cordelia.rpc.types.TorrentAction;
import cordelia.rpc.types.Torrents;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

public class Transmission extends AbstractDownloader {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(Transmission.class);
    private final TrClient client;
    private final String blocklistUrl;
    private final Config config;
    private final DelayQueue<DelayedTorrents> pausedTorrents = new DelayQueue<>();

    /*
            API 受限，实际实现起来意义不大

            */
    public Transmission(String name, String blocklistUrl, Config config, AlertManager alertManager) {
        super(name, alertManager);
        this.config = config;
        this.client = new TrClient(config.getEndpoint() + config.getRpcUrl(), config.getUsername(), config.getPassword(), config.isVerifySsl(), HttpClient.Version.valueOf(config.getHttpVersion()));
        this.blocklistUrl = blocklistUrl;
        log.warn(tlUI(Lang.DOWNLOADER_TR_MOTD_WARNING));
        log.warn(tlUI(Lang.DOWNLOADER_TRANSMISSION_DISCOURAGE));
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

    @SneakyThrows(InterruptedException.class)
    @Override
    public DownloaderLoginResult login0() {
        RqSessionGet get = new RqSessionGet();
        TypedResponse<RsSessionGet> resp = client.execute(get); // 执行任意 RPC 操作以刷新 session
        String version = resp.getArgs().getVersion();
        if (version.startsWith("0.") || version.startsWith("1.") || version.startsWith("2.")) {
            return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_TR_KNOWN_INCOMPATIBILITY, "API Version"));
        }
        if (!resp.getArgs().getBlocklistEnabled() || !resp.getArgs().getBlocklistUrl().startsWith(blocklistUrl)) {
            RqSessionSet set = RqSessionSet.builder()
                    .blocklistUrl(blocklistUrl + "?t=" + System.currentTimeMillis()) // 更改 URL 来确保更改生效
                    .blocklistEnabled(true)
                    .build();
            TypedResponse<RsSessionGet> sessionSetResp = client.execute(set);
            if (!sessionSetResp.isSuccess()) {
                log.error(tlUI(Lang.DOWNLOADER_TR_INCORRECT_BANLIST_API_RESP), sessionSetResp.getResult());
            }
            Thread.sleep(3000);
        }
        if (System.getProperty("pbh.downloader.transmission.i-know-transmission-is-discourage-and-could-be-removed-in-future-please-dont-show-require-take-actions-status-text-and-i-know-what-i-am-doing") != null) {
            return new DownloaderLoginResult(DownloaderLoginResult.Status.SUCCESS, new TranslationComponent(Lang.STATUS_TEXT_OK));
        } else {
            return new DownloaderLoginResult(DownloaderLoginResult.Status.REQUIRE_TAKE_ACTIONS, new TranslationComponent(Lang.DOWNLOADER_TRANSMISSION_DISCOURAGE));
        }
    }

    @Override
    public List<Torrent> getTorrents() {
        RqTorrentGet torrent = new RqTorrentGet(Fields.ID, Fields.HASH_STRING, Fields.NAME, Fields.PEERS_CONNECTED, Fields.STATUS, Fields.TOTAL_SIZE, Fields.PEERS, Fields.RATE_DOWNLOAD, Fields.RATE_UPLOAD, Fields.PEER_LIMIT, Fields.PERCENT_DONE);
        TypedResponse<RsTorrentGet> rsp = client.execute(torrent);
        return rsp.getArgs().getTorrents().stream()
                .filter(t -> t.getStatus() == Status.DOWNLOADING || t.getStatus() == Status.SEEDING)
                .filter(t -> !(config.isIgnorePrivate() && t.getIsPrivate()))
                .map(TRTorrent::new).collect(Collectors.toList());
    }

    @Override
    public List<Peer> getPeers(Torrent torrent) {
        TRTorrent trTorrent = (TRTorrent) torrent;
        return trTorrent.getPeers();
    }


    @SneakyThrows
    @Override
    public void setBanList(Collection<PeerAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed, boolean applyFullList) {
        RqBlockList updateBlockList = new RqBlockList();
        TypedResponse<RsBlockList> updateBlockListResp = client.execute(updateBlockList);
        if (!updateBlockListResp.isSuccess()) {
            log.error(tlUI(Lang.DOWNLOADER_TR_INCORRECT_SET_BANLIST_API_RESP));
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
        relaunchTorrents(torrents.stream()
                .filter(t -> {
                    try {
                        Long.parseLong(t.getId());
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }).map(t -> Long.parseLong(t.getId())).toList());
    }

    private void relaunchTorrents(Collection<Long> ids) {
        if (ids.isEmpty()) return;
        RqTorrentGet torrentList = new RqTorrentGet(Fields.ID, Fields.HASH_STRING, Fields.NAME,
                Fields.PEERS_CONNECTED,
                Fields.STATUS, Fields.TOTAL_SIZE, Fields.PEERS, Fields.RATE_DOWNLOAD,
                Fields.RATE_UPLOAD, Fields.PEER_LIMIT, Fields.PERCENT_DONE, Fields.IS_PRIVATE);
        TypedResponse<RsTorrentGet> rsp = client.execute(torrentList);
        List<Long> torrents = rsp.getArgs().getTorrents().stream()
                .filter(t -> t.getStatus() != Status.STOPPED)
                .map(Torrents::getId)
                .filter(ids::contains).collect(Collectors.toList());
        log.info(tlUI(Lang.DOWNLOADER_TR_DISCONNECT_PEERS, torrents.size()));
        RqTorrent stop = new RqTorrent(TorrentAction.STOP, new ArrayList<>());
        for (long torrent : torrents) {
            stop.add(torrent);
        }
        pausedTorrents.add(new DelayedTorrents(torrents, 5000));
        client.execute(stop);
        unpauseTorrents(false);
    }

    @Override
    public void relaunchTorrentIfNeededByTorrentWrapper(Collection<TorrentWrapper> torrents) {
        if(System.getProperty("pbh.transmission.disable-torrent-relaunch") != null) {
            return;
        }
        relaunchTorrents(torrents.stream().filter(t -> {
            try {
                Long.parseLong(t.getId());
                return true;
            } catch (Exception e) {
                return false;
            }
        }).map(t -> Long.parseLong(t.getId())).toList());
    }

    private synchronized void unpauseTorrents(boolean includeUnexpired) {
        List<DelayedTorrents> ids;
        if (includeUnexpired) {
            ids = new ArrayList<>(pausedTorrents);
            pausedTorrents.clear();
        } else {
            ids = new ArrayList<>();
            pausedTorrents.drainTo(ids);
        }
        if (ids.isEmpty()) {
            return;
        }
        RqTorrent resume = new RqTorrent(TorrentAction.START, new ArrayList<>());
        ids.stream().flatMap(d -> d.getTorrentIds().stream()).forEach(resume::add);
        try {
            var run = client.execute(resume);
            if (!run.isSuccess()) {
                pausedTorrents.addAll(ids);
            }
        } catch (Exception e) {
            log.warn("Unable handle Transmission task restore, scheduled re-run in next schedule task window.");
            pausedTorrents.addAll(ids);
        }
    }

    @Override
    public void runScheduleTasks() {
        unpauseTorrents(false);
    }

    @Override
    public void close() {
        unpauseTorrents(true);
        client.shutdown();
    }


    public static class DelayedTorrents implements Delayed {
        private final long start;
        @Getter
        private final List<Long> torrentIds;

        public DelayedTorrents(List<Long> torrentIds, long delay) {
            this.torrentIds = new ArrayList<>(torrentIds);
            this.start = System.currentTimeMillis() + delay;
        }

        @Override
        public long getDelay(@NotNull TimeUnit unit) {
            long diff = start - System.currentTimeMillis();
            return unit.convert(diff, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(@NotNull Delayed o) {
            return Ints.saturatedCast(this.start - ((DelayedTorrents) o).start);
        }
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
            return section;
        }
    }
}
