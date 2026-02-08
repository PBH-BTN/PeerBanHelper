package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.enhanced;

import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.DownloaderLoginResult;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.AbstractQbittorrent;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.QBittorrentPreferences;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.traversal.NatAddressProvider;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import inet.ipaddr.IPAddress;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class QBittorrentEE extends AbstractQbittorrent {
    private final BanHandler banHandler;

    public QBittorrentEE(String id, QBittorrentEEConfigImpl config, AlertManager alertManager, HTTPUtil httpUtil, NatAddressProvider natAddressProvider) {
        super(id, config, alertManager, httpUtil, natAddressProvider);
        if (config.isUseShadowBan()) {
            this.banHandler = new BanHandlerShadowBan(httpClient, config.getName(), apiEndpoint);
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

    public static QBittorrentEE loadFromConfig(String id, JsonObject section, AlertManager alertManager, HTTPUtil httpUtil, NatAddressProvider natAddressProvider) {
        QBittorrentEEConfigImpl config = JsonUtil.getGson().fromJson(section.toString(), QBittorrentEEConfigImpl.class);
        return new QBittorrentEE(id, config, alertManager, httpUtil, natAddressProvider);
    }

    public static QBittorrentEE loadFromConfig(String id, ConfigurationSection section, AlertManager alertManager, HTTPUtil httpUtil, NatAddressProvider natAddressProvider) {
        QBittorrentEEConfigImpl config = QBittorrentEEConfigImpl.readFromYaml(section, id);
        return new QBittorrentEE(id, config, alertManager, httpUtil, natAddressProvider);
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
    public @NotNull String getType() {
        return "qBittorrentEE";
    }


    @Override
    public void setBanList(@NotNull Collection<IPAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed, boolean applyFullList) {
        if (removed != null && removed.isEmpty() && added != null && config.isIncrementBan() && !applyFullList) {
            banHandler.setBanListIncrement(added);
        } else {
            banHandler.setBanListFull(fullList);
        }
    }

    @Override
    public @NotNull List<Peer> getPeers(@NotNull Torrent torrent) {
        try {
            Request request = new Request.Builder()
                    .url(apiEndpoint + "/sync/torrentPeers?hash=" + torrent.getId())
                    .get()
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IllegalStateException(tlUI(Lang.DOWNLOADER_QB_FAILED_REQUEST_PEERS_LIST_IN_TORRENT, response.code(), response.body() != null ? response.body().string() : "null"));
                }
                
                String responseBody = response.body().string();
                JsonObject object = JsonParser.parseString(responseBody).getAsJsonObject();
                JsonObject peers = object.getAsJsonObject("peers");
                
                List<Peer> peersList = new ArrayList<>();
                for (String s : peers.keySet()) {
                    JsonObject singlePeerObject = peers.getAsJsonObject(s);
                    QBittorrentEEPeer qbPeer = JsonUtil.getGson().fromJson(singlePeerObject.toString(), QBittorrentEEPeer.class);
                    if (qbPeer.getPeerAddress().getIp() == null || qbPeer.getPeerAddress().getIp().isBlank()) {
                        continue;
                    }
                    if ("HTTP".equalsIgnoreCase(qbPeer.getConnection()) || "HTTPS".equalsIgnoreCase(qbPeer.getConnection()) || "Web".equalsIgnoreCase(qbPeer.getConnection())) {
                        continue;
                    }
                    if (s.contains(".onion") || s.contains(".i2p")) {
                        continue;
                    }
                    if (qbPeer.getShadowBanned() != null && qbPeer.getShadowBanned()) {
                        continue; // 当做不存在处理
                    }
                    // 一个 QB 本地化问题的 Workaround
                    if (qbPeer.getPeerId() == null || "Unknown".equals(qbPeer.getPeerId()) || "未知".equals(qbPeer.getPeerId())) {
                        qbPeer.setPeerIdClient("");
                    }
                    if (qbPeer.getClientName() != null) {
                        if (qbPeer.getClientName().startsWith("Unknown [") && qbPeer.getClientName().endsWith("]")) {
                            String mid = qbPeer.getClientName().substring("Unknown [".length(), qbPeer.getClientName().length() - 1);
                            qbPeer.setClient(mid);
                        }
                    }
                    qbPeer.getPeerAddress().setRawIp(s);
                    qbPeer.setPeerAddress(natTranslate(qbPeer.getPeerAddress()));
                    peersList.add(qbPeer);
                }
                return peersList;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    interface BanHandler {
        boolean test();

        void setBanListIncrement(Collection<BanMetadata> added);

        void setBanListFull(Collection<IPAddress> peerAddresses);
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
        public void setBanListFull(Collection<IPAddress> peerAddresses) {
            downloader.setBanListFull(peerAddresses);
        }
    }

    public static class BanHandlerShadowBan implements BanHandler {

        private final OkHttpClient httpClient;
        private final String name;
        private final String apiEndpoint;
        private Boolean shadowBanEnabled = false; // 缓存 shadowBan 开关状态

        public BanHandlerShadowBan(OkHttpClient httpClient, String name, String apiEndpoint) {
            this.httpClient = httpClient;
            this.name = name;
            this.apiEndpoint = apiEndpoint;
        }

        @Override
        public boolean test() {
            if (shadowBanEnabled)
                return true;
            try {
                Request request = new Request.Builder()
                        .url(apiEndpoint + "/app/preferences")
                        .get()
                        .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IllegalStateException("Failed to get preferences: " + response.code());
                    }
                    String responseBody = response.body().string();
                    QBittorrentPreferences preferences = JsonUtil.getGson().fromJson(responseBody, QBittorrentPreferences.class);
                    shadowBanEnabled = preferences.getShadowBanEnabled() != null && preferences.getShadowBanEnabled();
                    return shadowBanEnabled;
                }
            } catch (Exception e) {
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
                FormBody formBody = new FormBody.Builder()
                        .add("hash", hash)
                        .add("peers", peers.toString())
                        .build();
                
                try {
                    Request request = new Request.Builder()
                            .url(apiEndpoint + "/transfer/shadowbanPeers")
                            .post(formBody)
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .build();
                    
                    try (Response response = httpClient.newCall(request).execute()) {
                        if (!response.isSuccessful()) {
                            log.error(tlUI(Lang.DOWNLOADER_QB_INCREAMENT_BAN_FAILED, name, apiEndpoint, response.code(), "HTTP ERROR", response.body() != null ? response.body().string() : "null"));
                            throw new IllegalStateException("Save qBittorrent shadow banlist error: statusCode=" + response.code());
                        }
                    }
                } catch (Exception e) {
                    log.error(tlUI(Lang.DOWNLOADER_QB_INCREAMENT_BAN_FAILED, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
                    throw new IllegalStateException(e);
                }
            });
        }

        @Override
        public void setBanListFull(Collection<IPAddress> peerAddresses) {
            StringJoiner joiner = new StringJoiner("\n");
            peerAddresses.stream().map(IPAddress::toNormalizedString).distinct().forEach(joiner::add);
            
            FormBody formBody = new FormBody.Builder()
                    .add("json", JsonUtil.getGson().toJson(Map.of("shadow_banned_IPs", joiner.toString())))
                    .build();
            
            try {
                Request request = new Request.Builder()
                        .url(apiEndpoint + "/app/setPreferences")
                        .post(formBody)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        log.error(tlUI(Lang.DOWNLOADER_QB_FAILED_SAVE_BANLIST, name, apiEndpoint, response.code(), "HTTP ERROR", response.body() != null ? response.body().string() : "null"));
                        throw new IllegalStateException("Save qBittorrent shadow banlist error: statusCode=" + response.code());
                    }
                }
            } catch (Exception e) {
                log.error(tlUI(Lang.DOWNLOADER_QB_FAILED_SAVE_BANLIST, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
                throw new IllegalStateException(e);
            }
        }
    }
}
