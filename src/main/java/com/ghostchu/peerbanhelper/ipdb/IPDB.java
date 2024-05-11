package com.ghostchu.peerbanhelper.ipdb;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.ipdb.ipproxy.IP2Proxy;
import com.ghostchu.peerbanhelper.ipdb.ipproxy.ProxyResult;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.github.mizosoft.methanol.MutableRequest;
import com.ip2location.IP2Location;
import com.ip2location.IPResult;
import inet.ipaddr.IPAddress;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class IPDB implements AutoCloseable {
    private final String token;
    private final File dataFolder;
    private final long updateInterval = 86400000L; // 30天
    //private ScheduledExecutorService UPDATE_SCHEDULER = Executors.newScheduledThreadPool(2);
    private IP2Proxy proxy;
    private IP2Location geoIpIPV4;
    private IP2Location geoIpIPV6;

    public IPDB(File dataFolder, String token) throws IllegalArgumentException, IOException {
        this.dataFolder = dataFolder;
        this.token = token;
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Set ip2location (lite) license token in config.yml");
        }
        checkDatabaseUpdate();
        openDatabase();
    }

    @Nullable
    public ProxyResult queryProxy(IPAddress ip) {
        try {
            return proxy.GetAll(ip.toString());
        } catch (IOException e) {
            log.warn("Query IP proxy data failed", e);
            return null;
        }
    }

    @Nullable
    public IPResult queryGeoIP(IPAddress ip) {
        try {
            if (ip.isIPv4Convertible()) {
                return geoIpIPV4.IPQuery(ip.toIPv4().toString());
            } else {
                return geoIpIPV6.IPQuery(ip.toIPv6().toString());
            }
        } catch (IOException e) {
            log.warn("Query IP geo data failed", e);
            return null;
        }
    }

    private void openDatabase() throws IOException {
        openProxyDatabase();
        openGeoIPDatabase();
    }

    private void openGeoIPDatabase() throws IOException {
        File directory = new File(dataFolder, "geoip");
        File ipv4 = new File(directory, "ipv4.bin");
        File ipv6 = new File(directory, "ipv6.bin");
        if (!ipv4.exists()) {
            throw new IllegalStateException("IPV4 GeoIP database not exists");
        }
        if (!ipv6.exists()) {
            throw new IllegalStateException("IPV6 GeoIP database not exists");
        }
        this.geoIpIPV4 = new IP2Location();
        this.geoIpIPV4.Open(ipv4.getPath());
        this.geoIpIPV6 = new IP2Location();
        this.geoIpIPV6.Open(ipv6.getPath());
    }

    private void openProxyDatabase() throws IOException {
        File directory = new File(dataFolder, "proxy");
        File proxy = new File(directory, "proxy.bin");
        if (!proxy.exists()) {
            throw new IllegalStateException("IPProxy database not exists");
        }
        this.proxy = new IP2Proxy();
        this.proxy.Open(proxy.getPath());
    }

    private void checkDatabaseUpdate() throws IOException {
        File ipdbConfigFile = new File(dataFolder, "ipdb-metadata.yml");
        ipdbConfigFile.getParentFile().mkdirs();
        if (!ipdbConfigFile.exists()) {
            ipdbConfigFile.createNewFile();
        }
        YamlConfiguration ipdbConfig = YamlConfiguration.loadConfiguration(ipdbConfigFile);
        long geoipUpdateAt = ipdbConfig.getLong("update-at.geoip", 0);
        long proxyUpdateAt = ipdbConfig.getLong("update-at.proxy", 0);
        try {
            if (System.currentTimeMillis() - geoipUpdateAt > updateInterval) {
                log.info(Lang.IPDB_UPDATING, "GeoIP", "GeoIP");
                updateGeoIp();
                ipdbConfig.set("update-at.geoip", System.currentTimeMillis());
            }
            if (System.currentTimeMillis() - proxyUpdateAt > updateInterval) {
                log.info(Lang.IPDB_UPDATING, "IPProxy", "IPProxy");
                updateProxy();
                ipdbConfig.set("update-at.proxy", System.currentTimeMillis());
            }
        } finally {
            ipdbConfig.save(ipdbConfigFile);
        }
    }

    private void updateProxy() throws IOException {
        File directory = new File(dataFolder, "proxy");
        directory.mkdirs();
        File proxy = new File(directory, "proxy.bin");
        String downloadUrl = getFileUrl("PX11LITEBIN");
        MutableRequest request = MutableRequest.GET(downloadUrl)
                .headers("User-Agent", Main.getUserAgent());
        Path proxyTmp = Files.createTempFile("ipdb-proxy", ".zip");
        downloadFile(request, proxyTmp, "PX11LITEBIN").join();
        extractFile(proxyTmp, "IP2PROXY-LITE-PX11.BIN", proxy.toPath());
    }

    private void updateGeoIp() throws IOException {
        File directory = new File(dataFolder, "geoip");
        directory.mkdirs();
        File ipv4 = new File(directory, "ipv4.bin");
        File ipv6 = new File(directory, "ipv6.bin");
        String ipv4Url = getFileUrl("DB11LITEBIN");
        String ipv6Url = getFileUrl("DB11LITEBINIPV6");
        MutableRequest request = MutableRequest.GET(ipv4Url)
                .headers("User-Agent", Main.getUserAgent());
        Path ipv4Tmp = Files.createTempFile("ipdb-ipv4", ".zip");
        Path ipv6Tmp = Files.createTempFile("ipdb-ipv6", ".zip");
        downloadFile(request, ipv4Tmp, "DB11LITEBIN").join();
        request = MutableRequest.GET(ipv6Url)
                .headers("User-Agent", Main.getUserAgent());
        downloadFile(request, ipv6Tmp, "DB11LITEBINIPV6").join();
        extractFile(ipv4Tmp, "IP2LOCATION-LITE-DB11.BIN", ipv4.toPath());
        extractFile(ipv6Tmp, "IP2LOCATION-LITE-DB11.IPV6.BIN", ipv6.toPath());
    }

    private void extractFile(Path zipFile, String fileName, Path outputFile) throws IOException {
        // Wrap the file system in a try-with-resources statement
        // to auto-close it when finished and prevent a memory leak
        try (FileSystem fileSystem = FileSystems.newFileSystem(zipFile)) {
            Path fileToExtract = fileSystem.getPath(fileName);
            Files.copy(fileToExtract, outputFile);
        }
    }

    private CompletableFuture<Void> downloadFile(MutableRequest req, Path path, String databaseName) {
        return HTTPUtil.retryableSendProgressTracking(HTTPUtil.getHttpClient(false, null), req, HttpResponse.BodyHandlers.ofFile(path))
                .thenAccept(r -> {
                    if (r.statusCode() != 200) {
                        log.warn(Lang.IPDB_UPDATE_FAILED, databaseName, r.statusCode() + " - " + r.body());
                    } else {
                        log.info(Lang.IPDB_UPDATE_SUCCESS, databaseName);
                    }
                })
                .exceptionally(e -> {
                    log.warn(Lang.IPDB_UPDATE_FAILED, "Java Exception", e);
                    File file = path.toFile();
                    if (file.exists()) {
                        file.delete(); // 删除下载不完整的文件
                    }
                    return null;
                });
    }

    private String getFileUrl(String databaseCode) {
        String template = "https://www.ip2location.com/download/?token=%s&file=%s";
        return String.format(template, token, databaseCode);
    }

    @Override
    public void close() throws Exception {
        if (this.proxy != null) {
            this.proxy.Close();
        }
        if (this.geoIpIPV4 != null) {
            this.geoIpIPV4.Close();
        }
        if (this.geoIpIPV6 != null) {
            this.geoIpIPV6.Close();
        }
    }
}
