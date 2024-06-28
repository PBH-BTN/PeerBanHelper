package com.ghostchu.peerbanhelper.downloader.impl.deluge;

import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderBasicAuth;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.downloader.WebViewScriptCallback;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
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

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Deluge implements Downloader {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(Deluge.class);
    private static final List<String> MUST_HAVE_METHODS = ImmutableList.of(
            "peerbanhelperadapter.replace_blocklist",
            "peerbanhelperadapter.unban_ips",
            "peerbanhelperadapter.get_active_torrents_info",
            "peerbanhelperadapter.ban_ips"
    );
    private final String name;
    private final DelugeServer client;
    private final Config config;
    private DownloaderLastStatus lastStatus = DownloaderLastStatus.UNKNOWN;
    private String statusMessage;

    public Deluge(String name, Config config) {
        this.name = name;
        this.config = config;
        this.client = new DelugeServer(config.getEndpoint() + config.getRpcUrl(), config.getPassword(), config.isVerifySsl(), HttpClient.Version.valueOf(config.getHttpVersion()), null, null);
    }

    public static Deluge loadFromConfig(String name, ConfigurationSection section) {
        Config config = Config.readFromYaml(section);
        return new Deluge(name, config);
    }

    public static Deluge loadFromConfig(String name, JsonObject section) {
        Config config = JsonUtil.getGson().fromJson(section.toString(), Config.class);
        return new Deluge(name, config);
    }

    private static String toStringHex(String s) {
        byte[] baKeyword = new byte[s.length() / 2];
        for (int i = 0; i < baKeyword.length; i++) {
            baKeyword[i] = (byte) (0xff & Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16));
        }
        return new String(baKeyword, StandardCharsets.ISO_8859_1);
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
    public String getWebUIEndpoint() {
        return config.getEndpoint();
    }

    @Override
    public @Nullable DownloaderBasicAuth getDownloaderBasicAuth() {
        return null;
    }

    @Override
    public @Nullable WebViewScriptCallback getWebViewJavaScript() {
        return null;
    }

    @Override
    public boolean isSupportWebview() {
        return true;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return "Deluge";
    }

    @Override
    public boolean login() {
        try {
            if (!this.client.login().isLoggedIn()) {
                return false;
            }
            DelugeListMethodsResponse listMethodsResponse = this.client.listMethods();
            if (!new HashSet<>(listMethodsResponse.getDelugeSupportedMethods()).containsAll(MUST_HAVE_METHODS)) {
                log.warn(Lang.DOWNLOADER_DELUGE_PLUGIN_NOT_INSTALLED, getName());
                return false;
            }
        } catch (DelugeException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    @Override
    public List<Torrent> getTorrents() {
        List<Torrent> torrents = new ArrayList<>();
        try {
            for (PBHActiveTorrentsResponse.ActiveTorrentsResponseDTO activeTorrent : this.client.getActiveTorrents().getActiveTorrents()) {
                List<Peer> peers = new ArrayList<>();
                for (PBHActiveTorrentsResponse.ActiveTorrentsResponseDTO.PeersDTO peer : activeTorrent.getPeers()) {
                    DelugePeer delugePeer = new DelugePeer(
                            new PeerAddress(peer.getIp(), peer.getPort()),
                            toStringHex(peer.getPeerId()),
                            peer.getClientName(),
                            peer.getTotalDownload(),
                            peer.getPayloadDownSpeed(),
                            peer.getTotalUpload(),
                            peer.getPayloadUpSpeed(),
                            peer.getProgress() / 100.0d,
                            parseFlag(peer.getFlags(), peer.getSource())
                    );
                    peers.add(delugePeer);
                }
                Torrent torrent = new DelugeTorrent(
                        activeTorrent.getId(),
                        activeTorrent.getName(),
                        activeTorrent.getInfoHash(),
                        activeTorrent.getProgress() / 100.0d,
                        activeTorrent.getSize(),
                        activeTorrent.getUploadPayloadRate(),
                        activeTorrent.getDownloadPayloadRate(),
                        peers
                );
                torrents.add(torrent);
            }
        } catch (DelugeException e) {
            log.warn(Lang.DOWNLOADER_DELUGE_API_ERROR, e);
        }
        return torrents;
    }

    @Override
    public List<Peer> getPeers(Torrent torrent) {
        if (!(torrent instanceof DelugeTorrent delugeTorrent)) {
            throw new IllegalStateException("The torrent object not a instance of DelugeTorrent");
        }
        return delugeTorrent.getPeers();
    }

    @SneakyThrows
    @Override
    public void setBanList(Collection<PeerAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed) {
        if (removed != null && removed.isEmpty() && added != null && config.isIncrementBan()) {
            setBanListIncrement(added);
        } else {
            setBanListFull(fullList);
        }
    }

    private void setBanListFull(Collection<PeerAddress> fullList) {
        try {
            this.client.replaceBannedPeers(fullList.stream().map(PeerAddress::getIp).toList());
        } catch (DelugeException e) {
            log.warn(Lang.DOWNLOADER_DELUGE_API_ERROR, e);
        }
    }

    private void setBanListIncrement(Collection<BanMetadata> added) {
        try {
            this.client.banPeers(added.stream().map(bm -> bm.getPeer().getAddress().getIp()).toList());
        } catch (DelugeException e) {
            log.warn(Lang.DOWNLOADER_DELUGE_API_ERROR, e);
        }
    }

    @Override
    public void relaunchTorrentIfNeeded(Collection<Torrent> torrents) {

    }

    @Override
    public void relaunchTorrentIfNeededByTorrentWrapper(Collection<TorrentWrapper> torrents) {

    }

    @Override
    public DownloaderLastStatus getLastStatus() {
        return lastStatus;
    }

    @Override
    public void setLastStatus(DownloaderLastStatus lastStatus, String statusMessage) {
        this.lastStatus = lastStatus;
        this.statusMessage = statusMessage;
    }

    @Override
    public String getLastStatusMessage() {
        return statusMessage;
    }

    @Override
    public void close() {

    }
    private String parseFlag(int peerFlag, int sourceFlag) {
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

        StringJoiner joiner = new StringJoiner(" ");

        if (interesting) {
            if (remoteChoked) {
                joiner.add("d");
            } else {
                joiner.add("D");
            }
        }
        if (remoteInterested) {
            if (choked) {
                joiner.add("u");
            } else {
                joiner.add("U");
            }
        }
        if (!remoteChoked && !interesting)
            joiner.add("K");
        if (!choked && !remoteInterested)
            joiner.add("?");
        if (optimisticUnchoke)
            joiner.add("O");
        if (snubbed)
            joiner.add("S");
        if (!localConnection)
            joiner.add("I");
        if (dht)
            joiner.add("H");
        if (pex)
            joiner.add("X");
        if (lsd)
            joiner.add("L");
        if (rc4Encrypted)
            joiner.add("E");
        if (plainTextEncrypted)
            joiner.add("e");
        if (utpSocket)
            joiner.add("P");

        return joiner.toString();
    }


    private boolean c2b(char c) {
        return c == '1';
    }

    private String readBits(int i, int bitLength) {
        StringBuilder builder = new StringBuilder();
        builder.append(Integer.toBinaryString(i));
        while (builder.length() < bitLength) {
            builder.append("0");
        }
        return builder.toString();
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

        public static Config readFromYaml(ConfigurationSection section) {
            Config config = new Config();
            config.setType("deluge");
            config.setEndpoint(section.getString("endpoint"));
            if (config.getEndpoint().endsWith("/")) { // 浏览器复制党 workaround 一下， 避免连不上的情况
                config.setEndpoint(config.getEndpoint().substring(0, config.getEndpoint().length() - 1));
            }
            config.setPassword(section.getString("password"));
            config.setRpcUrl(section.getString("rpc-url", "/json"));
            config.setHttpVersion(section.getString("http-version", "HTTP_1_1"));
            config.setVerifySsl(section.getBoolean("verify-ssl", true));
            config.setIncrementBan(section.getBoolean("increment-ban"));
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
            return section;
        }
    }
}
