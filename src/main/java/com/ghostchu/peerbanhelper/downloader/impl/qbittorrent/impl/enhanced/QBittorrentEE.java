package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.enhanced;

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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class QBittorrentEE extends AbstractQbittorrent {
    private final BanHandler banHandler;

    public QBittorrentEE(String name, QBittorrentEEConfigImpl config) {
        super(name, config);
        if (config.isUseShadowBan()) {
            this.banHandler = new BanHandlerShadowBan(httpClient, name, apiEndpoint);
        } else {
            this.banHandler = new BanHandlerNormal(this);
        }
    }

    public static QBittorrentEE loadFromConfig(String name, JsonObject section) {
        QBittorrentEEConfigImpl config = JsonUtil.getGson().fromJson(section.toString(), QBittorrentEEConfigImpl.class);
        return new QBittorrentEE(name, config);
    }

    public static QBittorrentEE loadFromConfig(String name, ConfigurationSection section) {
        QBittorrentEEConfigImpl config = QBittorrentEEConfigImpl.readFromYaml(section);
        return new QBittorrentEE(name, config);
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
        String body;
        try (Response resp = httpClient.newCall(new Request.Builder()
                .url(apiEndpoint + "/sync/torrentPeers?hash=" + torrent.getId())
                .build()).execute()) {
            if (resp.code() != 200) {
                throw new IllegalStateException(tlUI(Lang.DOWNLOADER_QB_FAILED_REQUEST_PEERS_LIST_IN_TORRENT, resp.code(), resp.body().string()));
            }
            body = resp.body().string();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        JsonObject object = JsonParser.parseString(body).getAsJsonObject();
        JsonObject peers = object.getAsJsonObject("peers");
        List<Peer> peersList = new ArrayList<>();
        for (String s : peers.keySet()) {
            JsonObject singlePeerObject = peers.getAsJsonObject(s);
            QBittorrentEEPeer qbPeer = JsonUtil.getGson().fromJson(singlePeerObject.toString(), QBittorrentEEPeer.class);
            if(qbPeer.getPeerAddress().getIp() == null || qbPeer.getPeerAddress().getIp().isBlank()){
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
            try (Response resp = httpClient.newCall(new Request.Builder()
                    .url(apiEndpoint + "/app/preferences")
                    .build()).execute()) {
                QBittorrentPreferences preferences = JsonUtil.getGson().fromJson(resp.body().string(), QBittorrentPreferences.class);
                shadowBanEnabled = preferences.getShadowBanEnabled() != null && preferences.getShadowBanEnabled();
                return shadowBanEnabled;
            } catch (IOException e) {
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
                try (Response resp = httpClient.newCall(new Request.Builder()
                        .url(apiEndpoint + "/transfer/shadowbanPeers")
                        .post(new FormBody.Builder()
                                .add("hash", hash)
                                .add("peers", peers.toString())
                                .build())
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .build()).execute()) {
                    if (resp.code() != 200) {
                        log.error(tlUI(Lang.DOWNLOADER_QB_INCREAMENT_BAN_FAILED, name, apiEndpoint, resp.code(), "HTTP ERROR", resp.body().string()));
                        throw new IllegalStateException("Save qBittorrent shadow banlist error: statusCode=" + resp.code());
                    }
                } catch (IOException e) {
                    log.error(tlUI(Lang.DOWNLOADER_QB_INCREAMENT_BAN_FAILED, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
                    throw new IllegalStateException(e);
                }
            });
        }

        @Override
        public void setBanListFull(Collection<PeerAddress> peerAddresses) {
            StringJoiner joiner = new StringJoiner("\n");
            peerAddresses.forEach(p -> joiner.add(p.getIp()));
            try (Response resp = httpClient.newCall(new Request.Builder()
                    .url(apiEndpoint + "/app/setPreferences")
                    .post(new FormBody.Builder()
                            .add("json", JsonUtil.getGson().toJson(Map.of("shadow_banned_IPs", joiner.toString())))
                            .build())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build()).execute()) {
                if (resp.code() != 200) {
                    log.error(tlUI(Lang.DOWNLOADER_QB_FAILED_SAVE_BANLIST, name, apiEndpoint, resp.code(), "HTTP ERROR", resp.body().string()));
                    throw new IllegalStateException("Save qBittorrent shadow banlist error: statusCode=" + resp.code());
                }
            } catch (IOException e) {
                log.error(tlUI(Lang.DOWNLOADER_QB_FAILED_SAVE_BANLIST, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
                throw new IllegalStateException(e);
            }
        }
    }
}
