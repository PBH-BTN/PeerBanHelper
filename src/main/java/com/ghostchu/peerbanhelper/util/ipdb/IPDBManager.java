package com.ghostchu.peerbanhelper.util.ipdb;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.LazyLoad;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.InetAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public class IPDBManager {
    private final Cache<String, IPDBResponse> geoIpCache = CacheBuilder.newBuilder()
            .expireAfterAccess(ExternalSwitch.parseInt("pbh.geoIpCache.timeout", 300000), TimeUnit.MILLISECONDS)
            .maximumSize(ExternalSwitch.parseInt("pbh.geoIpCache.size", 300))
            .softValues()
            .build();
    private final HTTPUtil hTTPUtil;
    @Getter
    @Nullable
    private IPDB ipdb = null;

    public IPDBManager(HTTPUtil hTTPUtil) {
        this.hTTPUtil = hTTPUtil;
        setupIPDB();
    }

    private void setupIPDB() {
        try {
            String accountId = Main.getMainConfig().getString("ip-database.account-id", "");
            String licenseKey = Main.getMainConfig().getString("ip-database.license-key", "");
            String databaseCity = Main.getMainConfig().getString("ip-database.database-city", "GeoLite2-City");
            String databaseASN = Main.getMainConfig().getString("ip-database.database-asn", "GeoLite2-ASN");
            boolean autoUpdate = Main.getMainConfig().getBoolean("ip-database.auto-update");
            this.ipdb = new IPDB(new File(Main.getDataDirectory(), "ipdb"), accountId, licenseKey,
                    databaseCity, databaseASN, autoUpdate, Main.getUserAgent(), hTTPUtil);
            Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().name("IPDB Shutdown Worker").unstarted(() -> {
                try {
                    if (ipdb != null) {
                        ipdb.close();
                    }
                } catch (Exception ignored) {
                }
            }));
        } catch (Exception e) {
            log.info(tlUI(Lang.IPDB_INVALID), e);
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
                        } catch (Exception ignored) {
                            return null;
                        }
                    }));
                }
            });
        } catch (ExecutionException e) {
            return new IPDBResponse(null);
        }
    }

    public record IPDBResponse(
            LazyLoad<IPGeoData> geoData
    ) {
    }
}
