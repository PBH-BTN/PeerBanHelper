package com.ghostchu.peerbanhelper.util.ipdb;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.backgroundtask.*;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.maxmind.db.*;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import io.sentry.Sentry;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Okio;
import org.jetbrains.annotations.NotNull;
import org.tukaani.xz.XZInputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private Reader geoCN;
    private List<String> languageTag;

    public IPDB(File dataFolder, String accountId, String licenseKey, String databaseCity, String databaseASN, boolean autoUpdate, String userAgent, HTTPUtil httpUtil, BackgroundTaskManager backgroundTaskManager) throws IllegalArgumentException, IOException {
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
            updateGeoCN(mmdbGeoCNFile);
        }
        loadMMDB();
    }

    public IPGeoData query(InetAddress address) {
        IPGeoData geoData = new IPGeoData();
        geoData.setAs(queryAS(address));
        geoData.setCountry(queryCountry(address));
        geoData.setCity(queryCity(address));
        geoData.setNetwork(queryNetwork(address));
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
        if(geoCN == null) {
            return;
        }
        try {
            CNLookupResult cnLookupResult = geoCN.get(address, CNLookupResult.class);
            if (cnLookupResult == null) {
                return;
            }
            // City Data
            IPGeoData.CityData cityResponse = Objects.requireNonNullElse(geoData.getCity(), new IPGeoData.CityData());
            String cityName = (cnLookupResult.getProvince() + " " + cnLookupResult.getCity() + " " + cnLookupResult.getDistricts()).trim();
            if (!cityName.isBlank()) {
                cityResponse.setName(cityName);
            }

            Integer code = null;
            if (cnLookupResult.getProvinceCode() != null) {
                code = cnLookupResult.getProvinceCode().intValue();
            }
            if (cnLookupResult.getCityCode() != null) {
                code = cnLookupResult.getCityCode().intValue();
            }
            if (cnLookupResult.getDistrictsCode() != null) {
                code = cnLookupResult.getDistrictsCode().intValue();
            }
            cityResponse.setIso(Long.parseLong("86" + code));
            cityResponse.setCnProvince(cnLookupResult.getProvince());
            cityResponse.setCnCity(cnLookupResult.getCity());
            cityResponse.setCnDistricts(cnLookupResult.getDistricts());
            geoData.setCity(cityResponse);
            // Network Data
            IPGeoData.NetworkData networkData = Objects.requireNonNullElse(geoData.getNetwork(), new IPGeoData.NetworkData());
            if (cnLookupResult.getIsp() != null && !cnLookupResult.getIsp().isBlank()) {
                networkData.setIsp(cnLookupResult.getIsp());
            }
            if (cnLookupResult.getNet() != null && !cnLookupResult.getNet().isBlank()) {
                TranslationComponent component = new TranslationComponent(cnLookupResult.getNet());
                switch (cnLookupResult.getNet()) {
                    case "宽带" -> new TranslationComponent(Lang.NET_TYPE_WIDEBAND);
                    case "基站" -> new TranslationComponent(Lang.NET_TYPE_BASE_STATION);
                    case "政企专线" -> new TranslationComponent(Lang.NET_TYPE_GOVERNMENT_AND_ENTERPRISE_LINE);
                    case "业务平台" -> new TranslationComponent(Lang.NET_TYPE_BUSINESS_PLATFORM);
                    case "骨干网" -> new TranslationComponent(Lang.NET_TYPE_BACKBONE_NETWORK);
                    case "IP专网" -> new TranslationComponent(Lang.NET_TYPE_IP_PRIVATE_NETWORK);
                    case "网吧" -> new TranslationComponent(Lang.NET_TYPE_INTERNET_CAFE);
                    case "物联网" -> new TranslationComponent(Lang.NET_TYPE_IOT);
                    case "数据中心" -> new TranslationComponent(Lang.NET_TYPE_DATACENTER);
                }
                networkData.setNetType(tlUI(component));
            }
            geoData.setNetwork(networkData);
        } catch (Exception e) {
            Sentry.captureException(e);
        }
    }

    private IPGeoData.NetworkData queryNetwork(InetAddress address) {
        if(mmdbASN == null) {
            return null;
        }
        try {
            IPGeoData.NetworkData networkData = new IPGeoData.NetworkData();
            AsnResponse asnResponse = mmdbASN.asn(address);
            networkData.setIsp(asnResponse.autonomousSystemOrganization());
            networkData.setNetType(null);
            return networkData;
        } catch (Exception e) {
            Sentry.captureException(e);
            return null;
        }
    }


    private IPGeoData.CityData queryCity(InetAddress address) {
        if(mmdbCity == null) {
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
            Sentry.captureException(e);
            return null;
        }
    }

    private IPGeoData.CountryData queryCountry(InetAddress address) {
        if(mmdbCity == null) {
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
            Sentry.captureException(e);
            return null;
        }
    }


    private IPGeoData.ASData queryAS(InetAddress address) {
        if(mmdbASN == null) {
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
            Sentry.captureException(e);
            return null;
        }
    }

    private void updateGeoCN(File mmdbGeoCNFile) {
        backgroundTaskManager.addTaskAsync(new FunctionalBackgroundTask(
                new TranslationComponent(Lang.IPDB_DOWNLOAD_MMDB),
                (task, callback) -> {
                    log.info(tlUI(Lang.IPDB_UPDATING, "GeoCN (github.com/ljxi/GeoCN)"));
                    IPDBDownloadSource mirror1 = new IPDBDownloadSource("https://github.com/ljxi/GeoCN/releases/download/Latest/", "GeoCN");
                    IPDBDownloadSource mirror3 = new IPDBDownloadSource("https://pbh-static.paulzzh.com/ipdb/", "GeoCN", true);
                    IPDBDownloadSource mirror4 = new IPDBDownloadSource("https://pbh-static.ghostchu.com/ipdb/", "GeoCN", true);
                    Path tmp = Files.createTempFile("GeoCN", ".mmdb");
                    downloadFile(tmp, "GeoCN", task, callback, mirror1, mirror3, mirror4).join();
                    if (!tmp.toFile().exists()) {
                        throw new IllegalStateException("Download mmdb database failed!");
                    }
                    Files.move(tmp, mmdbGeoCNFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
        )).join();
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
            this.geoCN = new Reader(mmdbGeoCNFile, Reader.FileMode.MEMORY_MAPPED, new MaxMindNodeCache());
        } catch (InvalidDatabaseException exception) {
            mmdbGeoCNFile.delete();
            mmdbGeoCNFile.deleteOnExit();
            log.error("Unable to load GeoCN database, the file may be corrupted. It has been deleted and will be re-downloaded on next startup.", exception);
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
                    Path tmp = Files.createTempFile(databaseName, ".mmdb");
                    downloadFile(tmp, databaseName, task, callback, mirror1, mirror3, mirror4).join();
                    if (!tmp.toFile().exists()) {
                        if (isMmdbNeverDownloaded(target)) {
                            throw new IllegalStateException("Download mmdb database failed!");
                        } else {
                            log.warn(tlUI(Lang.IPDB_EXISTS_UPDATE_FAILED, databaseName));
                        }
                    }
                    Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
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

    private CompletableFuture<Void> downloadFile(Path path, String databaseName, BackgroundTask bgTask, java.util.function.Consumer<BackgroundTask> callback, IPDBDownloadSource... mirrorList) {
        return downloadFile(Arrays.stream(mirrorList).collect(Collectors.toList()), path, databaseName, bgTask, callback);
    }

    private CompletableFuture<Void> downloadFile(List<IPDBDownloadSource> mirrorList, Path path, String databaseName, BackgroundTask bgTask, java.util.function.Consumer<BackgroundTask> callback) {
        return CompletableFuture.runAsync(() -> {
            IPDBDownloadSource mirror = mirrorList.removeFirst();
            // 创建带有进度追踪器的 HTTP 客户端
            bgTask.setStatusText(new TranslationComponent(Lang.IPDB_DOWNLOAD_MMDB_DESCRIPTION, mirror.getIPDBUrl()));
            callback.accept(bgTask);
            Request request = new Request.Builder()
                    .url(mirror.getIPDBUrl())
                    .get()
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                var body = response.body();
                long totalSize = body.contentLength();
                long totalRead = 0;
                bgTask.setMax(totalSize);
                if (totalSize <= 0) {
                    bgTask.setBarType(BackgroundTaskProgressBarType.INDETERMINATE);
                } else {
                    bgTask.setBarType(BackgroundTaskProgressBarType.DETERMINATE);
                }
                callback.accept(bgTask);
                if (response.code() == 200) {
                    if (mirror.supportXzip()) {
                        try {
                            File tmp = File.createTempFile(databaseName, ".tmp");
                            try (XZInputStream gzipInputStream = new XZInputStream(body.byteStream());
                                 FileOutputStream fileOutputStream = new FileOutputStream(tmp)) {
                                byte[] buffer = new byte[1024];
                                int len;
                                while ((len = gzipInputStream.read(buffer)) > 0) {
                                    totalRead += len;
                                    fileOutputStream.write(buffer, 0, len);
                                    bgTask.setCurrent(totalRead);
                                    callback.accept(bgTask);
                                }
                            }
                            bgTask.setBarType(BackgroundTaskProgressBarType.INDETERMINATE);
                            callback.accept(bgTask);
                            // validate mmdb
                            validateMMDB(tmp);
                            Files.move(tmp.toPath(), path, StandardCopyOption.REPLACE_EXISTING);
                            log.info(tlUI(Lang.IPDB_UPDATE_SUCCESS, databaseName));
                            return;
                        } catch (IOException e) {
                            log.warn(tlUI(Lang.IPDB_UNGZIP_FAILED, databaseName), e);
                        }
                    } else {
                        // 直接保存文件
                        try (var source = body.source();
                             var sink = Okio.buffer(Okio.sink(path))) {
                            sink.writeAll(source);
                            log.info(tlUI(Lang.IPDB_UPDATE_SUCCESS, databaseName));
                            return;
                        }
                    }
                }

                if (!mirrorList.isEmpty()) {
                    log.warn(tlUI(Lang.IPDB_RETRY_WITH_BACKUP_SOURCE));
                    downloadFile(mirrorList, path, databaseName, bgTask, callback).join();
                    return;
                }
                log.error(tlUI(Lang.IPDB_UPDATE_FAILED, databaseName, response.code() + " - " + response.body().string()));
            } catch (Exception e) {
                if (!mirrorList.isEmpty()) {
                    log.warn(tlUI(Lang.IPDB_RETRY_WITH_BACKUP_SOURCE));
                    downloadFile(mirrorList, path, databaseName, bgTask, callback).join();
                    return;
                }
                log.error(tlUI(Lang.IPDB_UPDATE_FAILED, databaseName, e.getMessage()), e);
                bgTask.setStatus(BackgroundTaskStatus.FAILED);
                bgTask.setStatusText(new TranslationComponent(Lang.IPDB_DOWNLOAD_MMDB_FAILED_DESCRIPTION, e.getMessage()));
                callback.accept(bgTask);
            }
        });
    }

    private void validateMMDB(File tmp) throws IOException {
        try(var reader = new Reader(tmp, NoCache.getInstance())) {
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
        if (this.geoCN != null) {
            try {
                this.geoCN.close();
            } catch (IOException ignored) {

            }
        }
    }

    @Getter
    @ToString
    public static class CNLookupResult {
        private final String isp;
        private final String net;
        private final String province;
        private final Long provinceCode;
        private final String city;
        private final Long cityCode;
        private final String districts;
        private final Long districtsCode;

        @MaxMindDbConstructor
        public CNLookupResult(
                @MaxMindDbParameter(name = "isp") String isp,
                @MaxMindDbParameter(name = "net") String net,
                @MaxMindDbParameter(name = "province") String province,
                @MaxMindDbParameter(name = "provinceCode") Object provinceCode,
                @MaxMindDbParameter(name = "city") String city,
                @MaxMindDbParameter(name = "cityCode") Object cityCode,
                @MaxMindDbParameter(name = "districts") String districts,
                @MaxMindDbParameter(name = "districtsCode") Object districtsCode
        ) {
            this.isp = isp;
            this.net = net;
            this.province = province;
            this.provinceCode = provinceCode != null ? Long.parseLong(provinceCode.toString()) : null;
            this.city = city;
            this.cityCode = cityCode != null ? Long.parseLong(cityCode.toString()) : null;
            this.districts = districts;
            this.districtsCode = districtsCode != null ? Long.parseLong(districtsCode.toString()) : null;
        }
    }

    public static final class MaxMindNodeCache implements NodeCache {
        private final static Cache<@NotNull CacheKey, @NotNull DecodedValue> cache = CacheBuilder.newBuilder()
                .maximumSize(2000)
                .expireAfterAccess(1, TimeUnit.HOURS)
                .build();

        @SneakyThrows
        @Override
        public DecodedValue get(CacheKey cacheKey, Loader loader) {
            return cache.get(cacheKey, () -> loader.load(cacheKey));
        }
    }

}
