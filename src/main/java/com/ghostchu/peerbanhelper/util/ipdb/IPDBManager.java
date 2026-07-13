package com.ghostchu.peerbanhelper.util.ipdb;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.LazyLoad;
import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTaskManager;
import com.ghostchu.peerbanhelper.util.lab.Laboratory;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.sentry.Sentry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public class IPDBManager {
    private final Cache<String, IPDBResponse> geoIpCache = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMillis(ExternalSwitch.parseInt("pbh.geoIpCache.timeout", 300000)))
            .maximumSize(ExternalSwitch.parseInt("pbh.geoIpCache.size", 300))
            .softValues()
            .build();
    private final HTTPUtil hTTPUtil;
    private final BackgroundTaskManager backgroundTaskManager;
    @Getter
    @Nullable
    private IPDB ipdb = null;

    public IPDBManager(HTTPUtil hTTPUtil, Laboratory laboratory, BackgroundTaskManager backgroundTaskManager) {
        this.hTTPUtil = hTTPUtil;
        this.backgroundTaskManager = backgroundTaskManager;
        if (!ExternalSwitch.parseBoolean("pbh.forceDisableIPDB")) {
            CompletableFuture.runAsync(this::setupIPDB);
        }
    }

    private void setupIPDB() {
        try {
            String accountId = Main.getMainConfig().getString("ip-database.account-id", "");
            String licenseKey = Main.getMainConfig().getString("ip-database.license-key", "");
            String databaseCity = Main.getMainConfig().getString("ip-database.database-city", "GeoLite2-City");
            String databaseASN = Main.getMainConfig().getString("ip-database.database-asn", "GeoLite2-ASN");
            String databaseGeoCN = Main.getMainConfig().getString("ip-database.database-geocn", "GeoCN");
            boolean autoUpdate = Main.getMainConfig().getBoolean("ip-database.auto-update");
            this.ipdb = new IPDB(new File(Main.getDataDirectory(), "ipdb"), accountId, licenseKey,
                    databaseCity, databaseASN, databaseGeoCN, autoUpdate, Main.getUserAgent(), hTTPUtil, backgroundTaskManager);
        } catch (Exception e) {
            log.info(tlUI(Lang.IPDB_INVALID), e);
            Sentry.captureException(e);
        }
    }

    public IPDBResponse queryIPDB(InetAddress address) {
        try {
            return geoIpCache.get(address.getHostAddress(), () -> {
                if (ipdb == null) {
                    return new IPDBResponse(new LazyLoad<>(() -> null));
                } else {
                    return new IPDBResponse(new LazyLoad<>(() -> {
                        try {
                            return ipdb.query(address);
                        } catch (Exception e) {
                            Sentry.captureException(e);
                            return null;
                        }
                    }));
                }
            });
        } catch (ExecutionException e) {
            Sentry.captureException(e);
            return new IPDBResponse(null);
        }
    }

    public void close() {
        if (ipdb != null) {
            ipdb.close();
        }
    }

    public record IPDBResponse(LazyLoad<IPGeoData> geoData) {
    }
}
