package com.ghostchu.peerbanhelper.downloader.impl.biglybt;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.AbstractDownloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLoginResult;
import com.ghostchu.peerbanhelper.downloader.DownloaderStatistics;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.ConnectorData;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.bean.clientbound.BanBean;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.bean.clientbound.BanListReplacementBean;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper.DownloadRecord;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper.PeerManagerRecord;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper.PeerRecord;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper.StatisticsRecord;
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
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.Interceptor;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;
import static com.ghostchu.peerbanhelper.util.HTTPUtil.MEDIA_TYPE_JSON;

public class BiglyBT extends AbstractDownloader {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(BiglyBT.class);
    private final String apiEndpoint;
    private final OkHttpClient httpClient;
    private final Config config;
    private final String connectorPayload;

    public BiglyBT(String name, Config config) {
        super(name);
        this.config = config;
        this.apiEndpoint = config.getEndpoint();
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        OkHttpClient.Builder builder = Main.getSharedHttpClient()
                .newBuilder()
                .followRedirects(true)
                .connectTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .readTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .cookieJar(new JavaNetCookieJar(cm))
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Interceptor.Chain chain) throws IOException {
                        Request original = chain.request();
                        Request request = original.newBuilder()
                                .header("Authorization", "Bearer " + config.getToken())
                                .header("Content-Type", "application/json")
                                //.header("Accept-Encoding", "gzip,deflate")
                                .method(original.method(), original.body())
                                .build();
                        return chain.proceed(request);
                    }
                });
        if (!config.isVerifySsl() && HTTPUtil.getIgnoreSSLSocketFactory() != null) {
            builder.sslSocketFactory(HTTPUtil.getIgnoreSSLSocketFactory(), HTTPUtil.getIgnoreTrustManager());
        }
        if (config.getHttpVersion().equals("HTTP_1_1")) {
            builder.protocols(Arrays.asList(Protocol.HTTP_1_1));
        }
        this.httpClient = builder.build();
        this.connectorPayload = JsonUtil.getGson().toJson(new ConnectorData("PeerBanHelper", Main.getMeta().getVersion(), Main.getMeta().getAbbrev()));
    }

    public static BiglyBT loadFromConfig(String name, JsonObject section) {
        Config config = JsonUtil.getGson().fromJson(section.toString(), Config.class);
        return new BiglyBT(name, config);
    }

    public static BiglyBT loadFromConfig(String name, ConfigurationSection section) {
        Config config = Config.readFromYaml(section);
        return new BiglyBT(name, config);
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
    public DownloaderLoginResult login0() {
        try (Response resp = httpClient.newCall(new Request.Builder().url(apiEndpoint + "/metadata").build()).execute()) {
            if (resp.code() == 200) {
                Request request = new Request.Builder()
                        .url(apiEndpoint + "/setconnector")
                        .post(RequestBody.create(connectorPayload,MEDIA_TYPE_JSON))
                        .build();
                try (Response ignored = httpClient.newCall(request).execute()) {
                } catch (IOException ignored) {
                }
                return new DownloaderLoginResult(DownloaderLoginResult.Status.SUCCESS, new TranslationComponent(Lang.STATUS_TEXT_OK));
            }
            if (resp.code() == 403) {
                return new DownloaderLoginResult(DownloaderLoginResult.Status.INCORRECT_CREDENTIAL, new TranslationComponent(Lang.DOWNLOADER_LOGIN_INCORRECT_CRED));
            }
            return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(Lang.DOWNLOADER_LOGIN_EXCEPTION, "statusCode=" + resp.code()));
        } catch (IOException e) {
            return new DownloaderLoginResult(DownloaderLoginResult.Status.NETWORK_ERROR, new TranslationComponent(Lang.DOWNLOADER_LOGIN_IO_EXCEPTION, e.getClass().getName() + ": " + e.getMessage()));
        }
    }

    @Override
    public String getEndpoint() {
        return apiEndpoint;
    }

    @Override
    public String getType() {
        return "BiglyBT";
    }

    @Override
    public void setBanList(@NotNull Collection<PeerAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed, boolean applyFullList) {
        if (removed != null && removed.isEmpty() && added != null && config.isIncrementBan() && !applyFullList) {
            setBanListIncrement(added);
        } else {
            setBanListFull(fullList);
        }
    }

    @Override
    public List<Torrent> getTorrents() {
        List<DownloadRecord> torrentDetail;
        try (Response resp = httpClient.newCall(new Request.Builder()
                .url(apiEndpoint + "/downloads?filter="
                        + BiglyBTDownloadStateConst.ST_DOWNLOADING
                        + "&filter=" + BiglyBTDownloadStateConst.ST_SEEDING
                        + "&filter=" + BiglyBTDownloadStateConst.ST_ERROR)
                .build()).execute()) {
            if (resp.code() != 200) {
                throw new IllegalStateException(tlUI(Lang.DOWNLOADER_BIGLYBT_INCORRECT_RESPONSE, resp.code(), resp.body().string()));
            }
            torrentDetail = JsonUtil.getGson().fromJson(resp.body().string(), new TypeToken<List<DownloadRecord>>() {
            }.getType());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        List<Torrent> torrents = new ArrayList<>();
        for (DownloadRecord detail : torrentDetail) {
            if (config.isIgnorePrivate() && detail.getTorrent().isPrivateTorrent()) {
                continue;
            }
            torrents.add(new TorrentImpl(
                    detail.getTorrent().getInfoHash(),
                    detail.getName(),
                    detail.getTorrent().getInfoHash(),
                    detail.getTorrent().getSize(),
                    detail.getStats().getCompletedInThousandNotation() / 1000d,
                    detail.getStats().getRtUploadSpeed(),
                    detail.getStats().getRtDownloadSpeed(),
                    detail.getTorrent().isPrivateTorrent()));
        }
        return torrents;
    }


    @Override
    public DownloaderStatistics getStatistics() {
        try (Response resp = httpClient.newCall(new Request.Builder().url(apiEndpoint + "/statistics").build()).execute()) {
            if (resp.code() != 200) {
                throw new IllegalStateException(tlUI(Lang.DOWNLOADER_FAILED_REQUEST_STATISTICS, resp.code(), resp.body().string()));
            }
            StatisticsRecord statisticsRecord = JsonUtil.getGson().fromJson(resp.body().string(), StatisticsRecord.class);
            return new DownloaderStatistics(statisticsRecord.getOverallDataBytesSent(), statisticsRecord.getOverallDataBytesReceived());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<Peer> getPeers(Torrent torrent) {
        PeerManagerRecord peerManagerRecord;
        List<Peer> peersList = new ArrayList<>(); // 不能为不可变列表
        try (Response resp = httpClient.newCall(new Request.Builder().url(apiEndpoint + "/download/" + torrent.getId() + "/peers").build()).execute()) {
            if (resp.code() == 404) { // 种子被删除或者种子错误时会返回 404
                return peersList;
            }
            if (resp.code() != 200) {
                throw new IllegalStateException(tlUI(Lang.DOWNLOADER_BIGLYBT_FAILED_REQUEST_PEERS_LIST_IN_TORRENT, resp.code(), resp.body().string()));
            }
            peerManagerRecord = JsonUtil.getGson().fromJson(resp.body().string(), PeerManagerRecord.class);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        for (PeerRecord peer : peerManagerRecord.getPeers()) {
            var peerId = new String(ByteUtil.hexToByteArray(peer.getPeerId()), StandardCharsets.ISO_8859_1);
            if (peerId.length() > 8) {
                peerId = peerId.substring(0, 8);
            }
            if (peer.getIp() == null || peer.getIp().isBlank()) {
                continue;
            }
            peersList.add(new PeerImpl(
                    new PeerAddress(peer.getIp(), peer.getPort()),
                    peer.getIp(),
                    peerId,
                    peer.getClient(),
                    peer.getStats().getRtDownloadSpeed(),
                    peer.getStats().getTotalReceived(),
                    peer.getStats().getRtUploadSpeed(),
                    peer.getStats().getTotalSent(),
                    peer.getPercentDoneInThousandNotation() / 1000d,
                    null
            ));
        }
        return peersList;
    }

    private void setBanListIncrement(Collection<BanMetadata> added) {
        BanBean bean = new BanBean(added.stream().map(b -> b.getPeer().getAddress().getIp()).toList());
        try (Response resp = httpClient.newCall(new Request.Builder()
                .url(apiEndpoint + "/bans")
                .post(RequestBody.create(JsonUtil.getGson().toJson(bean),MEDIA_TYPE_JSON))
                .build()).execute()) {
            if (resp.code() != 200) {
                log.error(tlUI(Lang.DOWNLOADER_BIGLYBT_INCREAMENT_BAN_FAILED, name, apiEndpoint, resp.code(), "HTTP ERROR", resp.body().string()));
                throw new IllegalStateException("Save BiglyBT banlist error: statusCode=" + resp.code());
            }
        } catch (IOException e) {
            log.error(tlUI(Lang.DOWNLOADER_BIGLYBT_INCREAMENT_BAN_FAILED, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
            throw new IllegalStateException(e);
        }
    }

    private void setBanListFull(Collection<PeerAddress> peerAddresses) {
        BanListReplacementBean bean = new BanListReplacementBean(peerAddresses.stream().map(PeerAddress::getIp).toList(), false);
        try (Response resp = httpClient.newCall(new Request.Builder()
                .url(apiEndpoint + "/bans")
                .put(RequestBody.create(JsonUtil.getGson().toJson(bean),MEDIA_TYPE_JSON))
                .build()).execute()) {
            if (resp.code() != 200) {
                log.error(tlUI(Lang.DOWNLOADER_BIGLYBT_FAILED_SAVE_BANLIST, name, apiEndpoint, resp.code(), "HTTP ERROR", resp.body().string()));
                throw new IllegalStateException("Save BiglyBT banlist error: statusCode=" + resp.code());
            }
        } catch (IOException e) {
            log.error(tlUI(Lang.DOWNLOADER_BIGLYBT_FAILED_SAVE_BANLIST, name, apiEndpoint, "N/A", e.getClass().getName(), e.getMessage()), e);
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {

    }

    @NoArgsConstructor
    @Data
    public static class Config {

        private String type;
        private String endpoint;
        private String token;
        private String httpVersion;
        private boolean incrementBan;
        private boolean verifySsl;
        private boolean ignorePrivate;

        public static Config readFromYaml(ConfigurationSection section) {
            Config config = new Config();
            config.setType("biglybt");
            config.setEndpoint(section.getString("endpoint"));
            if (config.getEndpoint().endsWith("/")) { // 浏览器复制党 workaround 一下， 避免连不上的情况
                config.setEndpoint(config.getEndpoint().substring(0, config.getEndpoint().length() - 1));
            }
            config.setToken(section.getString("token", ""));
            config.setIncrementBan(section.getBoolean("increment-ban", true));
            config.setHttpVersion(section.getString("http-version", "HTTP_1_1"));
            config.setVerifySsl(section.getBoolean("verify-ssl", true));
            config.setIgnorePrivate(section.getBoolean("ignore-private", false));
            return config;
        }

        public YamlConfiguration saveToYaml() {
            YamlConfiguration section = new YamlConfiguration();
            section.set("type", "biglybt");
            section.set("endpoint", endpoint);
            section.set("token", token);
            section.set("http-version", httpVersion);
            section.set("increment-ban", incrementBan);
            section.set("ignore-private", ignorePrivate);
            section.set("verify-ssl", verifySsl);
            return section;
        }
    }
}
