package com.ghostchu.peerbanhelper.downloader.impl.biglybt;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.peer.PeerFlag;
import com.ghostchu.peerbanhelper.bittorrent.peer.PeerImpl;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.bittorrent.tracker.Tracker;
import com.ghostchu.peerbanhelper.bittorrent.tracker.TrackerImpl;
import com.ghostchu.peerbanhelper.downloader.*;
import com.ghostchu.peerbanhelper.downloader.exception.DownloaderRequestException;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.BiglyBTTorrent;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.ConnectorData;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.bean.clientbound.BanBean;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.bean.clientbound.BanListReplacementBean;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.bean.clientbound.SetListenPort;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.bean.clientbound.SetSpeedLimiterBean;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.bean.serverbound.CurrentListenPort;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.bean.serverbound.CurrentSpeedLimiterBean;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.bean.serverbound.MetadataCallbackBean;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper.DownloadRecord;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper.PeerManagerRecord;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper.PeerRecord;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper.StatisticsRecord;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.ByteUtil;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.traversal.NatAddressProvider;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.vdurmont.semver4j.Semver;
import inet.ipaddr.IPAddress;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class BiglyBT extends AbstractDownloader {
    private final String apiEndpoint;
    private final OkHttpClient httpClient;
    private final Config config;
    private final String connectorPayload;

    public BiglyBT(String uuid, Config config, AlertManager alertManager, HTTPUtil httpUtil, NatAddressProvider natAddressProvider) {
        super(uuid, alertManager, natAddressProvider);
        this.config = config;
        this.apiEndpoint = config.getEndpoint();
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

        var builder = httpUtil.newBuilderForDownloader()
                .connectionPool(new ConnectionPool(getMaxConcurrentPeerRequestSlots() + 10, 5, TimeUnit.MINUTES))
                .addInterceptor(chain -> {
                    Request originalRequest = chain.request();
                    Request newRequest = originalRequest.newBuilder()
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + config.getToken())
                            .build();
                    return chain.proceed(newRequest);
                });
        httpUtil.disableSSLVerify(builder, !config.isVerifySsl());
        this.httpClient = builder.build();
        this.connectorPayload = JsonUtil.getGson().toJson(new ConnectorData("PeerBanHelper", Main.getMeta().getVersion(), Main.getMeta().getAbbrev()));
    }

    @Override
    public @NotNull String getName() {
        return config.getName();
    }

    public static BiglyBT loadFromConfig(String id, JsonObject section, AlertManager alertManager, HTTPUtil httpUtil, NatAddressProvider natAddressProvider) {
        Config config = JsonUtil.getGson().fromJson(section.toString(), Config.class);
        return new BiglyBT(id, config, alertManager, httpUtil, natAddressProvider);
    }

    public static BiglyBT loadFromConfig(String id, ConfigurationSection section, AlertManager alertManager, HTTPUtil httpUtil, NatAddressProvider natAddressProvider) {
        Config config = Config.readFromYaml(section, id);
        return new BiglyBT(id, config, alertManager, httpUtil, natAddressProvider);
    }

    @Override
    public @NotNull List<DownloaderFeatureFlag> getFeatureFlags() {
        return List.of(DownloaderFeatureFlag.READ_PEER_PROTOCOLS,
                DownloaderFeatureFlag.UNBAN_IP,
                DownloaderFeatureFlag.TRAFFIC_STATS,
                DownloaderFeatureFlag.LIVE_UPDATE_BT_PROTOCOL_PORT,
                DownloaderFeatureFlag.RANGE_BAN_IP);
    }

    @Override
    public DownloaderSpeedLimiter getSpeedLimiter() {
        Request request = new Request.Builder().url(apiEndpoint + "/speedlimiter").get().build();
        try (Response resp = httpClient.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new DownloaderRequestException(tlUI(Lang.DOWNLOADER_FAILED_RETRIEVE_SPEED_LIMITER, getName(), resp.code(), resp.body()));
            }
            CurrentSpeedLimiterBean currentSpeedLimit = JsonUtil.getGson().fromJson(resp.body().string(), CurrentSpeedLimiterBean.class);
            return new DownloaderSpeedLimiter(currentSpeedLimit.getUpload(), currentSpeedLimit.getDownload());
        } catch (IOException e) {
            throw new DownloaderRequestException(e);
        }
    }

    @Override
    public void setSpeedLimiter(@NotNull DownloaderSpeedLimiter speedLimiter) {
        SetSpeedLimiterBean bean = new SetSpeedLimiterBean(speedLimiter.upload(), speedLimiter.download());
        RequestBody requestBody = RequestBody.create(JsonUtil.getGson().toJson(bean), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(apiEndpoint + "/speedlimiter")
                .post(requestBody)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error(tlUI(Lang.DOWNLOADER_FAILED_SET_SPEED_LIMITER, getName(), response.code(), response.body().string()));
                throw new DownloaderRequestException("Save BiglyBT SpeedLimiter error: statusCode=" + response.code());
            }
        } catch (Exception e) {
            log.error(tlUI(Lang.DOWNLOADER_FAILED_SET_SPEED_LIMITER, getName(), "N/A", e.getMessage()), e);
            throw new IllegalStateException(e);
        }

    }

    @Override
    public int getBTProtocolPort() {
        Request request = new Request.Builder().url(apiEndpoint + "/listenport").get().build();
        try (Response resp = httpClient.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new DownloaderRequestException(tlUI(Lang.DOWNLOADER_FAILED_RETRIEVE_SPEED_LIMITER, getName(), resp.code(), resp.body()));
            }
            CurrentListenPort listenPort = JsonUtil.getGson().fromJson(resp.body().string(), CurrentListenPort.class);
            return listenPort.getPort();
        } catch (IOException e) {
            throw new DownloaderRequestException(e);
        }
    }

    @Override
    public void setBTProtocolPort(int port) {
        SetListenPort bean = new SetListenPort(port);
        RequestBody requestBody = RequestBody.create(JsonUtil.getGson().toJson(bean), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(apiEndpoint + "/listenport")
                .post(requestBody)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error(tlUI(Lang.DOWNLOADER_FAILED_SET_SPEED_LIMITER, getName(), response.code(), response.body().string()));
                throw new DownloaderRequestException("Save BiglyBT ListenPort error: statusCode=" + response.code());
            }
        } catch (Exception e) {
            log.error(tlUI(Lang.DOWNLOADER_FAILED_SET_SPEED_LIMITER, getName(), "N/A", e.getMessage()), e);
            throw new IllegalStateException(e);
        }
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
    public DownloaderLoginResult login0() {
        try {
            Request request = new Request.Builder().url(apiEndpoint + "/metadata").get().build();
            try (Response resp = httpClient.newCall(request).execute()) {
                if (resp.isSuccessful()) {
                    MetadataCallbackBean metadataCallbackBean = JsonUtil.standard().fromJson(resp.body().string(), MetadataCallbackBean.class);
                    // 验证版本号
                    var version = metadataCallbackBean.getPluginVersion();
                    Semver semver = new Semver(version);
                    // 检查是否大于等于 1.2.8
                    if (semver.isLowerThan("1.3.0")) {
                        return new DownloaderLoginResult(DownloaderLoginResult.Status.REQUIRE_TAKE_ACTIONS, new TranslationComponent(Lang.DOWNLOADER_BIGLYBT_INCORRECT_ADAPTER_VERSION, "1.3.0"));
                    }
                    RequestBody requestBody = RequestBody.create(connectorPayload, MediaType.get("application/json"));
                    Request setConnectorRequest = new Request.Builder()
                            .url(apiEndpoint + "/setconnector")
                            .post(requestBody)
                            .build();
                    try {
                        httpClient.newCall(setConnectorRequest).enqueue(new Callback() {
                            @Override
                            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                                log.warn("Unable to set connector for BiglyBT: {}", e.getMessage(), e);
                            }

                            @Override
                            public void onResponse(@NotNull Call call, @NotNull Response response) {
                                response.close();
                            }
                        });
                    } catch (Exception ignored) {
                    }
                    return new DownloaderLoginResult(DownloaderLoginResult.Status.SUCCESS, new TranslationComponent(Lang.STATUS_TEXT_OK));
                }
                if (resp.code() == 403) {
                    return new DownloaderLoginResult(DownloaderLoginResult.Status.INCORRECT_CREDENTIAL, new TranslationComponent(Lang.DOWNLOADER_LOGIN_INCORRECT_CRED));
                }
                return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_LOGIN_EXCEPTION, "statusCode=" + resp.code()));
            }
        } catch (Exception e) {
            return new DownloaderLoginResult(DownloaderLoginResult.Status.NETWORK_ERROR, new TranslationComponent(Lang.DOWNLOADER_LOGIN_IO_EXCEPTION, e.getClass().getName() + ": " + e.getMessage()));
        }
    }

    @Override
    public @NotNull String getEndpoint() {
        return apiEndpoint;
    }

    @Override
    public @NotNull String getType() {
        return "BiglyBT";
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
    public void setBanList(@NotNull Collection<IPAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed, boolean applyFullList) {
        if (removed != null && removed.isEmpty() && added != null && config.isIncrementBan() && !applyFullList) {
            setBanListIncrement(added);
        } else {
            setBanListFull(fullList);
        }
    }

    @Override
    public @NotNull List<Torrent> getTorrents() {
        return fetchTorrents(List.of(BiglyBTDownloadStateConst.ST_DOWNLOADING, BiglyBTDownloadStateConst.ST_SEEDING, BiglyBTDownloadStateConst.ST_ERROR), !config.isIgnorePrivate());
    }

    @Override
    public @NotNull List<Torrent> getAllTorrents() {
        return fetchTorrents(Collections.emptyList(), true);
    }

    private List<Torrent> fetchTorrents(List<Object> filtersUrlEncoded, boolean includePrivate) {
        StringBuilder urlBuilder = new StringBuilder(apiEndpoint + "/downloads");
        if (!filtersUrlEncoded.isEmpty()) {
            urlBuilder.append("?filter=");
            urlBuilder.append(String.join("&filter=", filtersUrlEncoded.stream().map(Object::toString).toArray(String[]::new)));
        }
        Request request = new Request.Builder()
                .url(urlBuilder.toString())
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException(tlUI(Lang.DOWNLOADER_BIGLYBT_INCORRECT_RESPONSE, response.code(), response.body().string()));
            }
            List<DownloadRecord> torrentDetail = JsonUtil.getGson().fromJson(response.body().string(), new TypeToken<List<DownloadRecord>>() {
            }.getType());
            List<Torrent> torrents = new ArrayList<>();
            for (DownloadRecord detail : torrentDetail) {
                if (!includePrivate && detail.getTorrent().isPrivateTorrent()) {
                    continue;
                }
                torrents.add(new BiglyBTTorrent(
                        detail.getTorrent().getInfoHash(),
                        detail.getName(),
                        detail.getTorrent().getInfoHash(),
                        detail.getTorrent().getSize(),
                        detail.getTorrent().getSize() - detail.getStats().getRemainingBytes(), // 种子总大小 减去 (包含未选择文件的)尚未下载大小 等于 已下载内容大小
                        detail.getStats().getCompletedInThousandNotation() / 1000d,
                        detail.getStats().getRtUploadSpeed(),
                        detail.getStats().getRtDownloadSpeed(),
                        detail.getTorrent().isPrivateTorrent(),
                        detail.getTrackers()));
            }
            return torrents;
        } catch (IOException e) {
            throw new DownloaderRequestException(e);
        }
    }

    @Override
    public int getMaxConcurrentPeerRequestSlots() {
        return ExternalSwitch.parseInt("pbh.downloader.BiglyBT.maxConcurrentPeerRequestSlots", 128);
    }

    @Override
    public @NotNull List<Tracker> getTrackers(@NotNull Torrent torrent) {
        BiglyBTTorrent biglyBTTorrent = (BiglyBTTorrent) torrent;
        if (biglyBTTorrent.getTrackers() == null) {
            return Collections.emptyList();
        }
        return biglyBTTorrent.getTrackers().stream().map(t -> (Tracker) new TrackerImpl(t)).toList();
    }

    @Override
    public void setTrackers(@NotNull Torrent torrent, @NotNull List<Tracker> trackers) {
        StringBuilder sb = new StringBuilder();
        for (Tracker tracker : trackers) {
            tracker.getTrackersInGroup().forEach(t -> sb.append(t).append("\n"));
            sb.append("\n");
        }
        RequestBody requestBody = RequestBody.create(sb.toString().trim(), MediaType.get("text/plain"));
        Request request = new Request.Builder()
                .url(apiEndpoint + "/download/" + torrent.getId() + "/trackers")
                .patch(requestBody)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new DownloaderRequestException(tlUI(Lang.TRACKER_FAILED_TO_UPDATE_TRACKER, torrent.getHash(), response.code(), response.body().string()));
            }
        } catch (IOException e) {
            throw new DownloaderRequestException(e);
        }
    }

    @Override
    public @NotNull DownloaderStatistics getStatistics() {
        Request request = new Request.Builder()
                .url(apiEndpoint + "/statistics")
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException(tlUI(Lang.DOWNLOADER_FAILED_REQUEST_STATISTICS, response.code(), response.body().string()));
            }
            StatisticsRecord statisticsRecord = JsonUtil.getGson().fromJson(response.body().string(), StatisticsRecord.class);
            return new DownloaderStatistics(statisticsRecord.getOverallDataBytesSent(), statisticsRecord.getOverallDataBytesReceived());
        } catch (IOException e) {
            throw new DownloaderRequestException(e);
        }
    }

    @Override
    public @NotNull List<Peer> getPeers(@NotNull Torrent torrent) {
        Request request = new Request.Builder()
                .url(apiEndpoint + "/download/" + torrent.getId() + "/peers")
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 404) { // 种子被删除或者种子错误时会返回 404
                return new ArrayList<>(); // 不能为不可变列表
            }
            if (!response.isSuccessful()) {
                throw new IllegalStateException(tlUI(Lang.DOWNLOADER_BIGLYBT_FAILED_REQUEST_PEERS_LIST_IN_TORRENT, response.code(), response.body().string()));
            }

            PeerManagerRecord peerManagerRecord = JsonUtil.getGson().fromJson(response.body().string(), PeerManagerRecord.class);
            List<Peer> peersList = new ArrayList<>();
            for (PeerRecord peer : peerManagerRecord.getPeers()) {
                var peerId = ByteUtil.hexToByteArray(peer.getPeerId());
                if (peer.getIp() == null || peer.getIp().isBlank()) {
                    continue;
                }
                if (peer.getIp().startsWith("/")) {
                    peer.setIp(peer.getIp().substring(1));
                }
                peersList.add(new PeerImpl(
                        natTranslate(new PeerAddress(peer.getIp(), peer.getPort(), peer.getIp())),
                        peerId,
                        peer.getClient(),
                        peer.getStats().getRtDownloadSpeed(),
                        peer.getStats().getTotalReceived(),
                        peer.getStats().getRtUploadSpeed(),
                        peer.getStats().getTotalSent(),
                        peer.getPercentDoneInThousandNotation() / 1000d,
                        parseFlag(peer),
                        peer.getState() != 30 && peer.getState() != 40
                ));
            }
            return peersList;
        } catch (IOException e) {
            throw new DownloaderRequestException(e);
        }
    }

    @Nullable
    private PeerFlag parseFlag(PeerRecord peer) {
        try {
            return new PeerFlag(
                    peer.isInteresting(),
                    peer.isChoking(),
                    peer.isInterested(),
                    peer.isChoked(),
                    false, // unsupported
                    !peer.isIncoming(),
                    !peer.isIncoming(),
                    peer.getState() == 20, // unsupported
                    peer.getState() == 10, // unsupported
                    false, // unsupported
                    peer.isSeed(),
                    peer.isOptimisticUnchoke(),
                    peer.isSnubbed(),
                    false, // unsupported, uploadOnly
                    false, // unsupported endGameMode
                    peer.getPeerSource() != null && "HolePunch".equals(peer.getPeerSource()),
                    peer.getIp() != null && peer.getIp().endsWith(".i2p"),
                    "uTP".equals(peer.getProtocol()),
                    false,
                    peer.isUseCrypto(),
                    false,
                    peer.getPeerSource() != null && "Tracker".equals(peer.getPeerSource()),
                    peer.getPeerSource() != null && "DHT".equals(peer.getPeerSource()),
                    peer.getPeerSource() != null && "PeerExchange".equals(peer.getPeerSource()),
                    false,
                    false,
                    peer.isIncoming()
            );
        }catch (Exception e) {
            log.debug("Failed to parse peer flag for peer: {}:{}: {}", peer.getIp(), peer.getPort(), peer, e);
            return null;
        }
    }

    private void setBanListIncrement(Collection<BanMetadata> added) {
        BanBean bean = new BanBean(added.stream()
                .map(b -> remapBanListAddress(b.getPeer().getAddress().getAddress()).toNormalizedString())
                .distinct().toList());
        RequestBody requestBody = RequestBody.create(JsonUtil.getGson().toJson(bean), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(apiEndpoint + "/bans")
                .post(requestBody)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error(tlUI(Lang.DOWNLOADER_BIGLYBT_INCREAMENT_BAN_FAILED, getName(), apiEndpoint, response.code(), "HTTP ERROR", response.body().string()));
                throw new IllegalStateException("Save BiglyBT banlist error: statusCode=" + response.code());
            }
        } catch (Exception e) {
            log.error(tlUI(Lang.DOWNLOADER_BIGLYBT_INCREAMENT_BAN_FAILED, getName(), apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
            throw new IllegalStateException(e);
        }
    }

    private void setBanListFull(Collection<IPAddress> peerAddresses) {
        BanListReplacementBean bean = new BanListReplacementBean(peerAddresses.stream()
                .map(ipaddr -> remapBanListAddress(ipaddr).toNormalizedString())
                .distinct().toList(), false);
        RequestBody requestBody = RequestBody.create(JsonUtil.getGson().toJson(bean), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(apiEndpoint + "/bans")
                .put(requestBody)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error(tlUI(Lang.DOWNLOADER_BIGLYBT_FAILED_SAVE_BANLIST, getName(), apiEndpoint, response.code(), "HTTP ERROR", response.body().string()));
                throw new IllegalStateException("Save BiglyBT banlist error: statusCode=" + response.code());
            }
        } catch (Exception e) {
            log.error(tlUI(Lang.DOWNLOADER_BIGLYBT_FAILED_SAVE_BANLIST, getName(), apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
            throw new IllegalStateException(e);
        }
    }


    @Override
    public void close() {

    }

    @NoArgsConstructor
    @Data
    public static class Config {
        private String name;
        private String type;
        private String endpoint;
        private String token;
        private boolean incrementBan;
        private boolean verifySsl;
        private boolean ignorePrivate;
        private boolean paused;

        public static Config readFromYaml(ConfigurationSection section, String alternativeName) {
            Config config = new Config();
            config.setType("biglybt");
            config.setEndpoint(section.getString("endpoint"));
            if (config.getEndpoint().endsWith("/")) { // 浏览器复制党 workaround 一下， 避免连不上的情况
                config.setEndpoint(config.getEndpoint().substring(0, config.getEndpoint().length() - 1));
            }
            config.setName(section.getString("name", alternativeName));
            config.setToken(section.getString("token", ""));
            config.setIncrementBan(section.getBoolean("increment-ban", true));
            config.setVerifySsl(section.getBoolean("verify-ssl", true));
            config.setIgnorePrivate(section.getBoolean("ignore-private", false));
            config.setPaused(section.getBoolean("paused", false));
            return config;
        }

        public YamlConfiguration saveToYaml() {
            YamlConfiguration section = new YamlConfiguration();
            section.set("type", "biglybt");
            section.set("name", name);
            section.set("endpoint", endpoint);
            section.set("token", token);
            section.set("increment-ban", incrementBan);
            section.set("ignore-private", ignorePrivate);
            section.set("verify-ssl", verifySsl);
            section.set("paused", paused);
            return section;
        }
    }
}
