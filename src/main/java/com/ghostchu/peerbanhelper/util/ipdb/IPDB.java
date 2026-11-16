package com.ghostchu.peerbanhelper.util.ipdb;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.backgroundtask.*;
import com.ghostchu.peerbanhelper.util.ipdb.geocn.GeoCN1;
import com.ghostchu.peerbanhelper.util.ipdb.geocn.GeoCN2;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.maxmind.db.*;
import com.maxmind.db.Reader;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import lombok.Cleanup;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.tukaani.xz.XZInputStream;

import java.io.*;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class IPDB implements AutoCloseable {
    private final File mmdbCityFile;
    private final File mmdbASNFile;
    private final boolean autoUpdate;
    private final File mmdbGeoCNFile;
    private final OkHttpClient httpClient;
    private final BackgroundTaskManager backgroundTaskManager;
    @Getter
    private DatabaseReader mmdbCity;
    @Getter
    private DatabaseReader mmdbASN;
    private List<String> languageTag;
    private GeoCN2 geoCN2;
    private GeoCN1 geoCN1;

    public IPDB(File dataFolder, String accountId, String licenseKey, String databaseCity, String databaseASN, String databaseGeoCN, boolean autoUpdate, String userAgent, HTTPUtil httpUtil, BackgroundTaskManager backgroundTaskManager) throws IllegalArgumentException, IOException {
//        this.dataFolder = dataFolder;
//        this.accountId = accountId;
//        this.licenseKey = licenseKey;
        File directory = new File(dataFolder, "geoip");
        directory.mkdirs();
        this.mmdbCityFile = new File(directory, "GeoIP-City.mmdb");
        this.mmdbASNFile = new File(directory, "GeoIP-ASN.mmdb");
        this.mmdbGeoCNFile = new File(directory, "GeoCN.mmdb");
        this.autoUpdate = autoUpdate;
        this.backgroundTaskManager = backgroundTaskManager;
//        this.userAgent = userAgent;
        this.httpClient = httpUtil.addProgressTracker(httpUtil.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .readTimeout(Duration.ofMinutes(3))
                        .callTimeout(Duration.ofMinutes(3))
                        .followRedirects(true)
                        .authenticator((route, response) -> {
                            if (response.request().header("Authorization") != null) {
                                return null; // 已经尝试过认证，不再重试
                            }
                            String credential = Credentials.basic(accountId, licenseKey);
                            return response.request().newBuilder().header("Authorization", credential).build();
                        }))
                .build();
        if (needUpdateMMDB(mmdbCityFile)) {
            updateMMDB(databaseCity, mmdbCityFile);
        }
        if (needUpdateMMDB(mmdbASNFile)) {
            updateMMDB(databaseASN, mmdbASNFile);
        }
        if (needUpdateMMDB(mmdbGeoCNFile)) {
            updateMMDB(databaseGeoCN, mmdbGeoCNFile);
        }
        loadMMDB();
    }

    public IPGeoData query(InetAddress address) {
        IPGeoData geoData = new IPGeoData();
        try {
            geoData.setAs(queryAS(address));
        } catch (Exception e) {
            log.debug("Unable to query AS", e);
        }
        try {
            geoData.setCountry(queryCountry(address));
        } catch (Exception e) {
            log.debug("Unable to query Country", e);
        }
        try {
            geoData.setCity(queryCity(address));
        } catch (Exception e) {
            log.debug("Unable to query City", e);
        }
        try {
            geoData.setNetwork(queryNetwork(address));
        } catch (Exception e) {
            log.debug("Unable to query Network", e);
        }
        if (geoData.getCountry() != null && geoData.getCountry().getIso() != null) {
            String iso = geoData.getCountry().getIso();
            if ("CN".equalsIgnoreCase(iso) || "TW".equalsIgnoreCase(iso)
                    || "HK".equalsIgnoreCase(iso) || "MO".equalsIgnoreCase(iso)) {
                queryGeoCN(address, geoData);
            }
        }
        return geoData;
    }

    private void queryGeoCN(InetAddress address, IPGeoData geoData) {
        try {
            var data = geoCN2.query(address);
            if (data != null) {
                geoData.mergeFrom(data, true);
            }
        } catch (IllegalStateException e) {
            try {
                var data = geoCN1.query(address);
                if (data != null) {
                    geoData.mergeFrom(data, true);
                }
            } catch (IOException ioe1) {
                log.debug("Unable to query GeoCN", ioe1);
            }
        } catch (IOException ioe) {
            log.debug("Unable to query GeoCN", ioe);
        }
    }

    private IPGeoData.NetworkData queryNetwork(InetAddress address) {
        if (mmdbASN == null) {
            return null;
        }
        try {
            IPGeoData.NetworkData networkData = new IPGeoData.NetworkData();
            AsnResponse asnResponse = mmdbASN.asn(address);
            networkData.setIsp(asnResponse.autonomousSystemOrganization());
            networkData.setNetType(null);
            return networkData;
        } catch (Exception e) {
            log.debug("Unable to query Network", e);
            return null;
        }
    }


    private IPGeoData.CityData queryCity(InetAddress address) {
        if (mmdbCity == null) {
            return null;
        }
        try {
            IPGeoData.CityData cityData = new IPGeoData.CityData();
            //IPGeoData.CityData.LocationData locationData = new IPGeoData.CityData.LocationData();
            CityResponse cityResponse = mmdbCity.city(address);
            City city = cityResponse.city();
//            Location location = cityResponse.location();
            cityData.setName(city.name());
            cityData.setIso(city.geonameId());
//            locationData.setTimeZone(location.timeZone());
//            locationData.setLongitude(location.longitude());
//            locationData.setLatitude(location.latitude());
//            locationData.setAccuracyRadius(location.accuracyRadius());
//            cityData.setLocation(locationData);
            return cityData;
        } catch (Exception e) {
            log.debug("Unable to query City", e);
            return null;
        }
    }

    private IPGeoData.CountryData queryCountry(InetAddress address) {
        if (mmdbCity == null) {
            return null;
        }
        try {
            IPGeoData.CountryData countryData = new IPGeoData.CountryData();
            CountryResponse countryResponse = mmdbCity.country(address);
            Country country = countryResponse.country();
            countryData.setIso(country.isoCode());
            String countryRegionName = country.name();
            // 对 TW,HK,MO 后处理，偷个懒
            var code = languageTag.getFirst();
            code = code.toLowerCase(Locale.ROOT).replace("-", "_");
            // 台湾、香港、澳门地区有一个独立 ISO 代码，需要手动处理一下保证符合所在地法律法规
            // 这坨代码已经改成一坨了，有时间得写个好点的 :(
            if (("zh_cn".equals(code) || "zh_hk".equals(code) || "zh_mo".equals(code)) && ("TW".equals(country.isoCode()) || "HK".equals(country.isoCode()) || "MO".equalsIgnoreCase(country.isoCode()))) {
                countryRegionName = "中国" + countryRegionName;
            }
            countryData.setName(countryRegionName);
            return countryData;
        } catch (Exception e) {
            log.debug("Unable to query Country", e);
            return null;
        }
    }


    private IPGeoData.ASData queryAS(InetAddress address) {
        if (mmdbASN == null) {
            return null;
        }
        try {
            IPGeoData.ASData asData = new IPGeoData.ASData();
            AsnResponse asnResponse = mmdbASN.asn(address);
            IPGeoData.ASData.ASNetwork network = new IPGeoData.ASData.ASNetwork();
            network.setPrefixLength(asnResponse.network().prefixLength());
            network.setIpAddress(asnResponse.network().networkAddress().getHostAddress());
            asData.setNumber(asnResponse.autonomousSystemNumber());
            asData.setOrganization(asnResponse.autonomousSystemOrganization());
            asData.setIpAddress(asnResponse.ipAddress().getHostAddress());
            asData.setNetwork(network);
            return asData;
        } catch (Exception e) {
            log.debug("Unable to query AS", e);
            return null;
        }
    }

    private void loadMMDB() throws IOException {
        this.languageTag = List.of(Main.DEF_LOCALE, "en");
        try {
            this.mmdbCity = new DatabaseReader.Builder(mmdbCityFile)
                    .locales(List.of(Main.DEF_LOCALE, "en"))
                    .fileMode(Reader.FileMode.MEMORY_MAPPED)
                    .withCache(new MaxMindNodeCache())
                    .build();
        } catch (InvalidDatabaseException exception) {
            mmdbCityFile.delete();
            mmdbCityFile.deleteOnExit();
            log.error("Unable to load GeoIP City database, the file may be corrupted. It has been deleted and will be re-downloaded on next startup.", exception);
        }
        try {
            this.mmdbASN = new DatabaseReader.Builder(mmdbASNFile)
                    .locales(List.of(Main.DEF_LOCALE, "en"))
                    .fileMode(Reader.FileMode.MEMORY_MAPPED)
                    .withCache(new MaxMindNodeCache())
                    .build();
        } catch (InvalidDatabaseException exception) {
            mmdbASNFile.delete();
            mmdbASNFile.deleteOnExit();
            log.error("Unable to load GeoIP ASN database, the file may be corrupted. It has been deleted and will be re-downloaded on next startup.", exception);
        }
        try {
            @Cleanup
            var divisionReader = new InputStreamReader(Main.class.getResourceAsStream("/ok_data_level3.csv"));
            this.geoCN2 = new GeoCN2(mmdbGeoCNFile, divisionReader, new MaxMindNodeCache());
        } catch (InvalidDatabaseException exception) {
            mmdbGeoCNFile.delete();
            mmdbGeoCNFile.deleteOnExit();
            log.error("Unable to load GeoCN database (version 2), the file may be corrupted. It has been deleted and will be re-downloaded on next startup.", exception);
        }
        try {
            this.geoCN1 = new GeoCN1(mmdbGeoCNFile, new MaxMindNodeCache());
        } catch (InvalidDatabaseException exception) {
            mmdbGeoCNFile.delete();
            mmdbGeoCNFile.deleteOnExit();
            log.error("Unable to load GeoCN database (version 1), the file may be corrupted. It has been deleted and will be re-downloaded on next startup.", exception);
        }
    }

    private void updateMMDB(String databaseName, File target) {
        backgroundTaskManager.addTaskAsync(new FunctionalBackgroundTask(
                new TranslationComponent(Lang.IPDB_DOWNLOAD_MMDB),
                (task, callback) -> {
                    log.info(tlUI(Lang.IPDB_UPDATING, databaseName));
                    IPDBDownloadSource mirror1 = new IPDBDownloadSource("https://github.com/PBH-BTN/GeoLite.mmdb/releases/latest/download/", databaseName, true);
                    IPDBDownloadSource mirror3 = new IPDBDownloadSource("https://pbh-static.paulzzh.com/ipdb/", databaseName, true);
                    IPDBDownloadSource mirror4 = new IPDBDownloadSource("https://pbh-static.ghostchu.com/ipdb/", databaseName, true);
                    if (!downloadFile(target.toPath(), databaseName, task, callback, mirror1, mirror3, mirror4).join()) {
                        if (isMmdbNeverDownloaded(target)) {
                            throw new IllegalStateException("Download mmdb database failed!");
                        } else {
                            log.warn(tlUI(Lang.IPDB_EXISTS_UPDATE_FAILED, databaseName));
                        }
                    }
                }
        )).join();
    }


    private boolean isMmdbNeverDownloaded(File target) {
        return !target.exists();
    }

    private boolean needUpdateMMDB(File target) {
        if (!target.exists()) {
            return true;
        }
        if (!autoUpdate) {
            return false;
        }
        // 45天
        long updateInterval = 3888000000L;
        return System.currentTimeMillis() - target.lastModified() > updateInterval;
    }

    private CompletableFuture<Boolean> downloadFile(Path path, String databaseName, BackgroundTask bgTask, java.util.function.Consumer<BackgroundTask> callback, IPDBDownloadSource... mirrorList) {
        return CompletableFuture.supplyAsync(() -> {
            Path target = path.toAbsolutePath();
            for (int i = 0; i < mirrorList.length; i++) {
                IPDBDownloadSource mirror = mirrorList[i];
                Path tmp = null;
                try {
                    bgTask.setCurrent(0);
                    bgTask.setStatusText(new TranslationComponent(Lang.IPDB_DOWNLOAD_MMDB_DESCRIPTION, mirror.getIPDBUrl()));
                    callback.accept(bgTask);
                    Request request = new Request.Builder().url(mirror.getIPDBUrl()).get().build();
                    // Keep staging files on the target filesystem so replacement can be atomic.
                    tmp = Files.createTempFile(target.getParent(), target.getFileName() + "-", ".tmp");
                    try (Response response = httpClient.newCall(request).execute()) {
                        if (response.code() != 200) {
                            throw new IOException("HTTP " + response.code() + " from " + mirror.getIPDBUrl());
                        }
                        var body = response.body();
                        if (body == null) {
                            throw new IOException("Empty response body from " + mirror.getIPDBUrl());
                        }
                        long totalSize = body.contentLength();
                        bgTask.setMax(totalSize);
                        bgTask.setBarType(totalSize <= 0 ? BackgroundTaskProgressBarType.INDETERMINATE : BackgroundTaskProgressBarType.DETERMINATE);
                        callback.accept(bgTask);
                        try (InputStream input = mirror.supportXzip() ? new XZInputStream(body.byteStream()) : body.byteStream();
                             OutputStream output = Files.newOutputStream(tmp)) {
                            byte[] buffer = new byte[8192];
                            long totalRead = 0;
                            int len;
                            while ((len = input.read(buffer)) != -1) {
                                output.write(buffer, 0, len);
                                totalRead += len;
                                bgTask.setCurrent(totalRead);
                                callback.accept(bgTask);
                            }
                        }
                    }
                    bgTask.setBarType(BackgroundTaskProgressBarType.INDETERMINATE);
                    callback.accept(bgTask);
                    // Validate both compressed and uncompressed downloads before touching the old database.
                    validateMMDB(tmp.toFile());
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    log.info(tlUI(Lang.IPDB_UPDATE_SUCCESS, databaseName));
                    return true;
                } catch (Exception e) {
                    log.warn(tlUI(Lang.IPDB_UPDATE_FAILED, databaseName, mirror.getIPDBUrl() + ": " + e.getMessage()), e);
                    if (i + 1 < mirrorList.length) {
                        log.warn(tlUI(Lang.IPDB_RETRY_WITH_BACKUP_SOURCE));
                    } else {
                        bgTask.setStatus(BackgroundTaskStatus.FAILED);
                        bgTask.setStatusText(new TranslationComponent(Lang.IPDB_DOWNLOAD_MMDB_FAILED_DESCRIPTION, e.getMessage()));
                        callback.accept(bgTask);
                    }
                } finally {
                    if (tmp != null) {
                        try {
                            Files.deleteIfExists(tmp);
                        } catch (IOException e) {
                            log.warn("Unable to delete temporary IP database {}", tmp, e);
                        }
                    }
                }
            }
            return false;
        });
    }

    private void validateMMDB(File tmp) throws IOException, InvalidNetworkException {
        try (InputStream is = new FileInputStream(tmp);
             var reader = new Reader(is, NoCache.getInstance())) {
            // Opening a reader only validates metadata; also decode records to detect corrupt data/tree pointers.
            var networks = reader.networks(Map.class);
            while (networks.hasNext()) {
                networks.next();
            }
            log.debug("Validate mmdb {} success: {}", tmp.getName(), reader.getMetadata().databaseType());
        }
    }

    @Override
    public void close() {
        if (this.mmdbCity != null) {
            try {
                this.mmdbCity.close();
            } catch (IOException ignored) {

            }
        }
        if (this.mmdbASN != null) {
            try {
                this.mmdbASN.close();
            } catch (IOException ignored) {

            }
        }
        if (this.geoCN2 != null) {
            try {
                this.geoCN2.close();
            } catch (Exception ignored) {

            }
        }
        if (this.geoCN1 != null) {
            try {
                this.geoCN1.close();
            } catch (Exception ignored) {

            }
        }
    }


    public static final class MaxMindNodeCache implements NodeCache {
        private final static Cache<@NotNull CacheKey, @NotNull DecodedValue> cache = CacheBuilder.newBuilder()
                .maximumSize(2000)
                .expireAfterAccess(Duration.ofHours(1))
                .build();

        @SneakyThrows
        @Override
        public DecodedValue get(CacheKey cacheKey, Loader loader) {
            return cache.get(cacheKey, () -> loader.load(cacheKey));
        }
    }

}
