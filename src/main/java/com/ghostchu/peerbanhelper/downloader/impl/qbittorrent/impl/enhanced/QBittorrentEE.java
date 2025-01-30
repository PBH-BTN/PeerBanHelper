package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.enhanced;

import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.downloader.DownloaderLoginResult;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.AbstractQbittorrent;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.QBittorrentPreferences;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.github.mizosoft.methanol.FormBodyPublisher;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class QBittorrentEE extends AbstractQbittorrent {
    private final BanHandler banHandler;

    public QBittorrentEE(String name, QBittorrentEEConfigImpl config, AlertManager alertManager) {
        super(name, config, alertManager);
        if (config.isUseShadowBan()) {
            this.banHandler = new BanHandlerShadowBan(httpClient, name, apiEndpoint);
        } else {
            this.banHandler = new BanHandlerNormal(this);
        }
    }

    @Override
    public boolean isPaused() {
        return config.isPaused();
    }

    @Override
    public synchronized void setPaused(boolean paused) {
        super.setPaused(paused);
        if (config != null) {
            config.setPaused(paused);
        }
    }

    public static QBittorrentEE loadFromConfig(String name, JsonObject section, AlertManager alertManager) {
        QBittorrentEEConfigImpl config = JsonUtil.getGson().fromJson(section.toString(), QBittorrentEEConfigImpl.class);
        return new QBittorrentEE(name, config, alertManager);
    }

    public static QBittorrentEE loadFromConfig(String name, ConfigurationSection section, AlertManager alertManager) {
        QBittorrentEEConfigImpl config = QBittorrentEEConfigImpl.readFromYaml(section);
        return new QBittorrentEE(name, config, alertManager);
    }

    public DownloaderLoginResult login0() {
        var result = super.login0();
        if (result.success()) {
            try {
                if (config.isUseShadowBan() && !banHandler.test()) {
                    return new DownloaderLoginResult(DownloaderLoginResult.Status.REQUIRE_TAKE_ACTIONS, new TranslationComponent(Lang.DOWNLOADER_QBITTORRENTEE_SHADOWBANAPI_TEST_FAILURE));
                }
            } catch (Exception e) {
                return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_LOGIN_IO_EXCEPTION, e.getClass().getName() + ": " + e.getMessage()));
            }
        }
        return result;
    }

    @Override
    public String getType() {
        return "qBittorrentEE";
    }


    @Override
    public void setBanList(@NotNull Collection<PeerAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed, boolean applyFullList) {
        if (removed != null && removed.isEmpty() && added != null && config.isIncrementBan() && !applyFullList) {
            banHandler.setBanListIncrement(added);
        } else {
            banHandler.setBanListFull(fullList);
        }
    }


    @Override
    public List<Peer> getPeers(Torrent torrent) {
        HttpResponse<String> resp;
        try {
            resp = httpClient.send(MutableRequest.GET(apiEndpoint + "/sync/torrentPeers?hash=" + torrent.getId()),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (resp.statusCode() != 200) {
            throw new IllegalStateException(tlUI(Lang.DOWNLOADER_QB_FAILED_REQUEST_PEERS_LIST_IN_TORRENT, resp.statusCode(), resp.body()));
        }

        JsonObject object = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonObject peers = object.getAsJsonObject("peers");
        List<Peer> peersList = new ArrayList<>();
        for (String s : peers.keySet()) {
            JsonObject singlePeerObject = peers.getAsJsonObject(s);
            QBittorrentEEPeer qbPeer = JsonUtil.getGson().fromJson(singlePeerObject.toString(), QBittorrentEEPeer.class);
            if(qbPeer.getPeerAddress().getIp() == null || qbPeer.getPeerAddress().getIp().isBlank()){
                continue;
            }
            if ("HTTP".equalsIgnoreCase(qbPeer.getConnection()) || "HTTPS".equalsIgnoreCase(qbPeer.getConnection()) || "Web".equalsIgnoreCase(qbPeer.getConnection())) {
                continue;
            }
            if(qbPeer.getRawIp().contains(".onion") || qbPeer.getRawIp().contains(".i2p")){
                continue;
            }
            if (qbPeer.getShadowBanned() != null && qbPeer.getShadowBanned()) {
                continue; // 当做不存在处理
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

    interface BanHandler {
        boolean test();

        void setBanListIncrement(Collection<BanMetadata> added);

        void setBanListFull(Collection<PeerAddress> peerAddresses);
    }

    public static class BanHandlerNormal implements BanHandler {

        private final QBittorrentEE downloader;

        public BanHandlerNormal(QBittorrentEE downloader) {
            this.downloader = downloader;
        }

        @Override
        public boolean test() {
            return true;
        }

        @Override
        public void setBanListIncrement(Collection<BanMetadata> added) {
            downloader.setBanListIncrement(added);
        }

        @Override
        public void setBanListFull(Collection<PeerAddress> peerAddresses) {
            downloader.setBanListFull(peerAddresses);
        }
    }

    public static class BanHandlerShadowBan implements BanHandler {

        private final HttpClient httpClient;
        private final String name;
        private final String apiEndpoint;
        private Boolean shadowBanEnabled = false; // 缓存 shadowBan 开关状态

        public BanHandlerShadowBan(HttpClient httpClient, String name, String apiEndpoint) {
            this.httpClient = httpClient;
            this.name = name;
            this.apiEndpoint = apiEndpoint;
        }

        @Override
        public boolean test() {
            if (shadowBanEnabled)
                return true;
            try {
                HttpResponse<String> request = httpClient.send(MutableRequest.GET(apiEndpoint + "/app/preferences")
                        , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                QBittorrentPreferences preferences = JsonUtil.getGson().fromJson(request.body(), QBittorrentPreferences.class);
                shadowBanEnabled = preferences.getShadowBanEnabled() != null && preferences.getShadowBanEnabled();
                return shadowBanEnabled;
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void setBanListIncrement(Collection<BanMetadata> added) {
            Map<String, StringJoiner> banTasks = new HashMap<>();
            added.forEach(p -> {
                StringJoiner joiner = banTasks.getOrDefault(p.getTorrent().getHash(), new StringJoiner("|"));
                joiner.add(p.getPeer().getRawIp());
                banTasks.put(p.getTorrent().getHash(), joiner);
            });
            banTasks.forEach((hash, peers) -> {
                try {
                    HttpResponse<String> request = httpClient.send(MutableRequest
                                    .POST(apiEndpoint + "/transfer/shadowbanPeers", FormBodyPublisher.newBuilder()
                                            .query("hash", hash)
                                            .query("peers", peers.toString()).build())
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                            , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (request.statusCode() != 200) {
                        log.error(tlUI(Lang.DOWNLOADER_QB_INCREAMENT_BAN_FAILED, name, apiEndpoint, request.statusCode(), "HTTP ERROR", request.body()));
                        throw new IllegalStateException("Save qBittorrent shadow banlist error: statusCode=" + request.statusCode());
                    }
                } catch (Exception e) {
                    log.error(tlUI(Lang.DOWNLOADER_QB_INCREAMENT_BAN_FAILED, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
                    throw new IllegalStateException(e);
                }
            });
        }

        @Override
        public void setBanListFull(Collection<PeerAddress> peerAddresses) {
            StringJoiner joiner = new StringJoiner("\n");
            peerAddresses.stream().map(PeerAddress::getIp).distinct().forEach(joiner::add);
            try {
                HttpResponse<String> request = httpClient.send(MutableRequest
                                .POST(apiEndpoint + "/app/setPreferences", FormBodyPublisher.newBuilder()
                                        .query("json", JsonUtil.getGson().toJson(Map.of("shadow_banned_IPs", joiner.toString()))).build())
                                .header("Content-Type", "application/x-www-form-urlencoded")
                        , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (request.statusCode() != 200) {
                    log.error(tlUI(Lang.DOWNLOADER_QB_FAILED_SAVE_BANLIST, name, apiEndpoint, request.statusCode(), "HTTP ERROR", request.body()));
                    throw new IllegalStateException("Save qBittorrent shadow banlist error: statusCode=" + request.statusCode());
                }
            } catch (Exception e) {
                log.error(tlUI(Lang.DOWNLOADER_QB_FAILED_SAVE_BANLIST, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
                throw new IllegalStateException(e);
            }
        }
    }
}
