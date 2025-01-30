package com.ghostchu.peerbanhelper.downloader.impl.deluge;

import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.downloader.AbstractDownloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLoginResult;
import com.ghostchu.peerbanhelper.downloader.DownloaderStatistics;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.peer.PeerFlag;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.torrent.Tracker;
import com.ghostchu.peerbanhelper.util.StrUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import raccoonfink.deluge.DelugeException;
import raccoonfink.deluge.DelugeServer;
import raccoonfink.deluge.responses.DelugeListMethodsResponse;
import raccoonfink.deluge.responses.PBHActiveTorrentsResponse;
import raccoonfink.deluge.responses.PBHStatisticsResponse;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

public final class Deluge extends AbstractDownloader {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(Deluge.class);
    private static final List<String> MUST_HAVE_METHODS = ImmutableList.of(
            "peerbanhelperadapter.replace_blocklist",
            "peerbanhelperadapter.unban_ips",
            "peerbanhelperadapter.get_active_torrents_info",
            "peerbanhelperadapter.ban_ips"
    );
    private final DelugeServer client;
    private final Config config;

    public Deluge(String name, Config config, AlertManager alertManager) {
        super(name, alertManager);
        this.name = name;
        this.config = config;
        this.client = new DelugeServer(config.getEndpoint() + config.getRpcUrl(), config.getPassword(), config.isVerifySsl(), HttpClient.Version.valueOf(config.getHttpVersion()), null, null);
    }

    public static Deluge loadFromConfig(String name, ConfigurationSection section, AlertManager alertManager) {
        Config config = Config.readFromYaml(section);
        return new Deluge(name, config, alertManager);
    }

    public static Deluge loadFromConfig(String name, JsonObject section, AlertManager alertManager) {
        Config config = JsonUtil.getGson().fromJson(section.toString(), Config.class);
        return new Deluge(name, config, alertManager);
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
        return "Deluge";
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
        try {
            if (!this.client.login().isLoggedIn()) {
                return new DownloaderLoginResult(DownloaderLoginResult.Status.INCORRECT_CREDENTIAL, new TranslationComponent(Lang.DOWNLOADER_LOGIN_INCORRECT_CRED));
            }
            DelugeListMethodsResponse listMethodsResponse = this.client.listMethods();
            if (!new HashSet<>(listMethodsResponse.getDelugeSupportedMethods()).containsAll(MUST_HAVE_METHODS)) {
                return new DownloaderLoginResult(DownloaderLoginResult.Status.MISSING_COMPONENTS, new TranslationComponent(Lang.DOWNLOADER_DELUGE_PLUGIN_NOT_INSTALLED));
            }
            return new DownloaderLoginResult(DownloaderLoginResult.Status.SUCCESS, new TranslationComponent(Lang.STATUS_TEXT_OK));
        } catch (DelugeException e) {
            return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_LOGIN_IO_EXCEPTION, e.getClass().getName() + ": " + e.getMessage()));
        }
    }

    @Override
    public List<Torrent> getTorrents() {
        List<Torrent> torrents = new ArrayList<>();
        try {
            for (PBHActiveTorrentsResponse.ActiveTorrentsResponseDTO activeTorrent : this.client.getActiveTorrents().getActiveTorrents()) {
                List<Peer> peers = new ArrayList<>();
                for (PBHActiveTorrentsResponse.ActiveTorrentsResponseDTO.PeersDTO peer : activeTorrent.getPeers()) {
                    var peerId = StrUtil.toStringHex(peer.getPeerId());
                    if (peerId.length() > 8) {
                        peerId = peerId.substring(0, 8);
                    }
                    DelugePeer delugePeer = new DelugePeer(
                            new PeerAddress(peer.getIp(), peer.getPort()),
                            peerId,
                            peer.getClientName(),
                            peer.getTotalDownload(),
                            peer.getPayloadDownSpeed(),
                            peer.getTotalUpload(),
                            peer.getPayloadUpSpeed(),
                            peer.getProgress() / 100.0d,
                            parsePeerFlag(peer.getFlags(), peer.getSource())
                    );
                    if (delugePeer.getPeerAddress().getIp() != null && !delugePeer.getPeerAddress().getIp().isBlank()) {
                        peers.add(delugePeer);
                    }
                }
                Torrent torrent = new DelugeTorrent(
                        activeTorrent.getId(),
                        activeTorrent.getName(),
                        activeTorrent.getInfoHash(),
                        activeTorrent.getProgress() / 100.0d,
                        activeTorrent.getSize(),
                        activeTorrent.getCompletedSize(),
                        activeTorrent.getUploadPayloadRate(),
                        activeTorrent.getDownloadPayloadRate(),
                        peers,
                        activeTorrent.getPriv() != null && activeTorrent.getPriv()
                );
                torrents.add(torrent);
            }
        } catch (DelugeException e) {
            log.error(tlUI(Lang.DOWNLOADER_DELUGE_API_ERROR), e);
        }
        return torrents;
    }

    @Override
    public List<Torrent> getAllTorrents() {
        return getTorrents();
    }

    @Override
    public List<Peer> getPeers(Torrent torrent) {
        if (!(torrent instanceof DelugeTorrent delugeTorrent)) {
            throw new IllegalStateException("The torrent object not a instance of DelugeTorrent");
        }
        return delugeTorrent.getPeers();
    }

    @Override
    public List<Tracker> getTrackers(Torrent torrent) {
        return List.of();
    }

    @Override
    public void setTrackers(Torrent torrent, List<Tracker> trackers) {

    }

    @SneakyThrows
    @Override
    public void setBanList(Collection<PeerAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed, boolean applyFullList) {
        if (removed != null && removed.isEmpty() && added != null && config.isIncrementBan() && !applyFullList) {
            setBanListIncrement(added);
        } else {
            setBanListFull(fullList);
        }
    }

    private void setBanListFull(Collection<PeerAddress> fullList) {
        try {
            this.client.replaceBannedPeers(fullList.stream().map(PeerAddress::getIp).distinct().toList());
        } catch (DelugeException e) {
            log.error(tlUI(Lang.DOWNLOADER_DELUGE_API_ERROR), e);
        }
    }

    private void setBanListIncrement(Collection<BanMetadata> added) {
        try {
            this.client.banPeers(added.stream().map(bm -> bm.getPeer().getAddress().getIp()).distinct().toList());
        } catch (DelugeException e) {
            log.error(tlUI(Lang.DOWNLOADER_DELUGE_API_ERROR), e);
        }
    }

    @Override
    public DownloaderStatistics getStatistics() {
        try {
            PBHStatisticsResponse resp = this.client.queryStatistics();
            var dto = resp.getStatistics();
            return new DownloaderStatistics(dto.getTotalPayloadUpload(), dto.getTotalPayloadDownload());
        } catch (DelugeException e) {
            log.error(tlUI(Lang.DOWNLOADER_DELUGE_API_ERROR), e);
        }
        return new DownloaderStatistics(0,0);
    }

    @Override
    public void close() {

    }

    private PeerFlag parsePeerFlag(int peerFlag, int sourceFlag) {
        boolean interesting = (peerFlag & (1 << 0)) != 0;
        boolean choked = (peerFlag & (1 << 1)) != 0;
        boolean remoteInterested = (peerFlag & (1 << 2)) != 0;
        boolean remoteChoked = (peerFlag & (1 << 3)) != 0;
        boolean supportsExtensions = (peerFlag & (1 << 4)) != 0;
        boolean outgoingConnection = (peerFlag & (1 << 5)) != 0;
        boolean localConnection = (peerFlag & (1 << 6)) != 0;
        boolean handshake = (peerFlag & (1 << 7)) != 0;
        boolean connecting = (peerFlag & (1 << 8)) != 0;
        boolean onParole = (peerFlag & (1 << 9)) != 0;
        boolean seed = (peerFlag & (1 << 10)) != 0;
        boolean optimisticUnchoke = (peerFlag & (1 << 11)) != 0;
        boolean snubbed = (peerFlag & (1 << 12)) != 0;
        boolean uploadOnly = (peerFlag & (1 << 13)) != 0;
        boolean endGameMode = (peerFlag & (1 << 14)) != 0;
        boolean holePunched = (peerFlag & (1 << 15)) != 0;
        boolean i2pSocket = (peerFlag & (1 << 16)) != 0;
        boolean utpSocket = (peerFlag & (1 << 17)) != 0;
        boolean sslSocket = (peerFlag & (1 << 18)) != 0;
        boolean rc4Encrypted = (peerFlag & (1 << 19)) != 0;
        boolean plainTextEncrypted = (peerFlag & (1 << 20)) != 0;

        boolean tracker = (sourceFlag & (1 << 0)) != 0;
        boolean dht = (sourceFlag & (1 << 1)) != 0;
        boolean pex = (sourceFlag & (1 << 2)) != 0;
        boolean lsd = (sourceFlag & (1 << 3)) != 0;
        boolean resumeData = (sourceFlag & (1 << 4)) != 0;
        boolean incoming = (sourceFlag & (1 << 5)) != 0;

        return new PeerFlag(interesting, choked, remoteInterested, remoteChoked, supportsExtensions, outgoingConnection, localConnection, handshake,
                connecting, onParole, seed, optimisticUnchoke, snubbed, uploadOnly, endGameMode, holePunched, i2pSocket, utpSocket, sslSocket,
                rc4Encrypted, plainTextEncrypted, tracker, dht, pex, lsd, resumeData, incoming);
    }

    @NoArgsConstructor
    @Data
    public static class Config {

        private String type;
        private String endpoint;
        private String password;
        private String httpVersion;
        private boolean verifySsl;
        private String rpcUrl;
        private boolean incrementBan;
        private boolean ignorePrivate;
        private boolean paused;

        public static Config readFromYaml(ConfigurationSection section) {
            Config config = new Config();
            config.setType("deluge");
            config.setEndpoint(section.getString("endpoint", ""));
            if (config.getEndpoint().endsWith("/")) { // 浏览器复制党 workaround 一下， 避免连不上的情况
                config.setEndpoint(config.getEndpoint().substring(0, config.getEndpoint().length() - 1));
            }
            config.setPassword(section.getString("password", ""));
            config.setRpcUrl(section.getString("rpc-url", "/json"));
            config.setHttpVersion(section.getString("http-version", "HTTP_1_1"));
            config.setVerifySsl(section.getBoolean("verify-ssl", true));
            config.setIncrementBan(section.getBoolean("increment-ban", true));
            config.setIgnorePrivate(section.getBoolean("ignore-private", false));
            config.setPaused(section.getBoolean("paused", false));
            return config;
        }

        public YamlConfiguration saveToYaml() {
            YamlConfiguration section = new YamlConfiguration();
            section.set("type", "deluge");
            section.set("endpoint", endpoint);
            section.set("password", password);
            section.set("rpc-url", rpcUrl);
            section.set("http-version", httpVersion);
            section.set("increment-ban", incrementBan);
            section.set("verify-ssl", verifySsl);
            section.set("ignore-private", ignorePrivate);
            section.set("paused", paused);
            return section;
        }
    }
}
