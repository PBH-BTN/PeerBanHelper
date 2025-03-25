package com.ghostchu.peerbanhelper.ipdb;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.gui.impl.console.ConsoleProgressDialog;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import com.github.mizosoft.methanol.ProgressTracker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.maxmind.db.*;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Location;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class IPDB implements AutoCloseable {
    private final File dataFolder;
    private final long updateInterval = 3888000000L; // 45天
    private final String accountId;
    private final String licenseKey;
    private final File directory;
    private final File mmdbCityFile;
    private final File mmdbASNFile;
    private final boolean autoUpdate;
    private final String userAgent;
    private final File mmdbGeoCNFile;
    private final Methanol httpClient;
    @Getter
    private DatabaseReader mmdbCity;
    @Getter
    private DatabaseReader mmdbASN;
    private Reader geoCN;
    private List<String> languageTag;

    public IPDB(File dataFolder, String accountId, String licenseKey, String databaseCity, String databaseASN, boolean autoUpdate, String userAgent) throws IllegalArgumentException, IOException {
        this.dataFolder = dataFolder;
        this.accountId = accountId;
        this.licenseKey = licenseKey;
        this.directory = new File(dataFolder, "geoip");
        this.directory.mkdirs();
        this.mmdbCityFile = new File(directory, "GeoIP-City.mmdb");
        this.mmdbASNFile = new File(directory, "GeoIP-ASN.mmdb");
        this.mmdbGeoCNFile = new File(directory, "GeoCN.mmdb");
        this.autoUpdate = autoUpdate;
        this.userAgent = userAgent;
        this.httpClient = Methanol
                .newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .userAgent(userAgent)
                .requestTimeout(Duration.of(2, ChronoUnit.MINUTES))
                .connectTimeout(Duration.of(15, ChronoUnit.SECONDS))
                .headersTimeout(Duration.of(15, ChronoUnit.SECONDS))
                .readTimeout(Duration.of(30, ChronoUnit.SECONDS), Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory()))
                .authenticator(new Authenticator() {
                    @Override
                    public PasswordAuthentication requestPasswordAuthenticationInstance(String host, InetAddress addr, int port, String protocol, String prompt, String scheme, URL url, RequestorType reqType) {
                        return new PasswordAuthentication(accountId, licenseKey.toCharArray());
                    }
                })
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
            if (iso.equalsIgnoreCase("CN") || iso.equalsIgnoreCase("TW")
                    || iso.equalsIgnoreCase("HK") || iso.equalsIgnoreCase("MO")) {
                queryGeoCN(address, geoData);
            }
        }
        return geoData;

    }

    private void queryGeoCN(InetAddress address, IPGeoData geoData) {
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
        } catch (Exception ignored) {
        }
    }

    private IPGeoData.NetworkData queryNetwork(InetAddress address) {
        try {
            IPGeoData.NetworkData networkData = new IPGeoData.NetworkData();
            AsnResponse asnResponse = mmdbASN.asn(address);
            networkData.setIsp(asnResponse.getAutonomousSystemOrganization());
            networkData.setNetType(null);
            return networkData;
        } catch (Exception ignored) {
            return null;
        }
    }


    private IPGeoData.CityData queryCity(InetAddress address) {

        try {
            IPGeoData.CityData cityData = new IPGeoData.CityData();
            IPGeoData.CityData.LocationData locationData = new IPGeoData.CityData.LocationData();
            CityResponse cityResponse = mmdbCity.city(address);
            City city = cityResponse.getCity();
            Location location = cityResponse.getLocation();
            cityData.setName(city.getName());
            cityData.setIso(city.getGeoNameId());
            locationData.setTimeZone(location.getTimeZone());
            locationData.setLongitude(location.getLongitude());
            locationData.setLatitude(location.getLatitude());
            locationData.setAccuracyRadius(location.getAccuracyRadius());
            cityData.setLocation(locationData);
            return cityData;
        } catch (Exception e) {
            return null;
        }

    }

    private IPGeoData.CountryData queryCountry(InetAddress address) {
        try {
            IPGeoData.CountryData countryData = new IPGeoData.CountryData();
            CountryResponse countryResponse = mmdbCity.country(address);
            Country country = countryResponse.getCountry();
            countryData.setIso(country.getIsoCode());
            String countryRegionName = country.getName();
            // 对 TW,HK,MO 后处理，偷个懒
            var code = languageTag.getFirst();
            code = code.toLowerCase(Locale.ROOT).replace("-", "_");
            // 台湾、香港、澳门地区有一个独立 ISO 代码，需要手动处理一下保证符合所在地法律法规
            // 这坨代码已经改成一坨了，有时间得写个好点的 :(
            if ((code.equals("zh_cn") || code.equals("zh_hk") || code.equals("zh_mo")) && (country.getIsoCode().equals("TW") || country.getIsoCode().equals("HK") || country.getIsoCode().equalsIgnoreCase("MO"))) {
                countryRegionName = "中国" + countryRegionName;
            }
            countryData.setName(countryRegionName);
            return countryData;
        } catch (Exception ignored) {
            return null;
        }
    }


    private IPGeoData.ASData queryAS(InetAddress address) {
        try {
            IPGeoData.ASData asData = new IPGeoData.ASData();
            AsnResponse asnResponse = mmdbASN.asn(address);
            IPGeoData.ASData.ASNetwork network = new IPGeoData.ASData.ASNetwork();
            network.setPrefixLength(asnResponse.getNetwork().getPrefixLength());
            network.setIpAddress(asnResponse.getNetwork().getNetworkAddress().getHostAddress());
            asData.setNumber(asnResponse.getAutonomousSystemNumber());
            asData.setOrganization(asnResponse.getAutonomousSystemOrganization());
            asData.setIpAddress(asnResponse.getIpAddress());
            asData.setNetwork(network);
            return asData;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void updateGeoCN(File mmdbGeoCNFile) throws IOException {
        log.info(tlUI(Lang.IPDB_UPDATING, "GeoCN (github.com/ljxi/GeoCN)"));
        IPDBDownloadSource mirror1 = new IPDBDownloadSource("https://github.com/ljxi/GeoCN/releases/download/Latest/", "GeoCN");
        //IPDBDownloadSource mirror2 = new IPDBDownloadSource("https://ghp.ci/https://github.com/ljxi/GeoCN/releases/download/Latest/", "GeoCN");
        IPDBDownloadSource mirror3 = new IPDBDownloadSource("https://pbh-static.paulzzh.com/ipdb/", "GeoCN", true);
        IPDBDownloadSource mirror4 = new IPDBDownloadSource("https://pbh-static.ghostchu.com/ipdb/", "GeoCN", true);
        Path tmp = Files.createTempFile("GeoCN", ".mmdb");
        downloadFile(tmp, "GeoCN", mirror1, mirror3, mirror4).join();
        if (!tmp.toFile().exists()) {
            throw new IllegalStateException("Download mmdb database failed!");
        }
        Files.move(tmp, mmdbGeoCNFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }


    private void loadMMDB() throws IOException {
        this.languageTag = List.of(Main.DEF_LOCALE, "en");
        this.mmdbCity = new DatabaseReader.Builder(mmdbCityFile)
                .locales(List.of(Main.DEF_LOCALE, "en"))
                .fileMode(Reader.FileMode.MEMORY_MAPPED)
                .withCache(new MaxMindNodeCache())
                .build();
        this.mmdbASN = new DatabaseReader.Builder(mmdbASNFile)
                .locales(List.of(Main.DEF_LOCALE, "en"))
                .fileMode(Reader.FileMode.MEMORY_MAPPED)
                .withCache(new MaxMindNodeCache())
                .build();
        this.geoCN = new Reader(mmdbGeoCNFile, Reader.FileMode.MEMORY_MAPPED, new MaxMindNodeCache());
    }

    private void updateMMDB(String databaseName, File target) throws IOException {
        log.info(tlUI(Lang.IPDB_UPDATING, databaseName));
        IPDBDownloadSource mirror1 = new IPDBDownloadSource("https://github.com/PBH-BTN/GeoLite.mmdb/releases/latest/download/", databaseName, true);
        //IPDBDownloadSource mirror2 = new IPDBDownloadSource("https://ghp.ci/https://github.com/P3TERX/GeoLite.mmdb/releases/latest/download/", databaseName);
        IPDBDownloadSource mirror3 = new IPDBDownloadSource("https://pbh-static.paulzzh.com/ipdb/", databaseName, true);
        IPDBDownloadSource mirror4 = new IPDBDownloadSource("https://pbh-static.ghostchu.com/ipdb/", databaseName, true);
        Path tmp = Files.createTempFile(databaseName, ".mmdb");
        downloadFile(tmp, databaseName, mirror1, mirror3, mirror4).join();
        if (!tmp.toFile().exists()) {
            if (isMmdbNeverDownloaded(target)) {
                throw new IllegalStateException("Download mmdb database failed!");
            } else {
                log.warn(tlUI(Lang.IPDB_EXISTS_UPDATE_FAILED, databaseName));
            }
        }
        Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
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
        return System.currentTimeMillis() - target.lastModified() > updateInterval;
    }

    private CompletableFuture<Void> downloadFile(Path path, String databaseName, IPDBDownloadSource... mirrorList) {
        return downloadFile(Arrays.stream(mirrorList).collect(Collectors.toList()), path, databaseName);
    }

    private CompletableFuture<Void> downloadFile(List<IPDBDownloadSource> mirrorList, Path path, String databaseName) {
        IPDBDownloadSource mirror = mirrorList.removeFirst();
        ProgressTracker tracker = ProgressTracker.newBuilder()
                .bytesTransferredThreshold(16 * 1024) // 16 kB
                .timePassedThreshold(Duration.of(1, ChronoUnit.SECONDS))
                .build();
        var progressDialog = Main.getGuiManager().createProgressDialog(tlUI(Lang.IPDB_DOWNLOAD_TITLE, databaseName), tlUI(Lang.IPDB_DOWNLOAD_DESCRIPTION, databaseName), tlUI(Lang.GUI_COMMON_CANCEL), null, false);
        progressDialog.show();
        progressDialog.setComment(mirror.getIPDBUrl());
        var bodyHandler = tracker.tracking(HttpResponse.BodyHandlers.ofFile(path), item -> {
            if (!(progressDialog instanceof ConsoleProgressDialog)) {
                HTTPUtil.onProgress(item);
            }
            progressDialog.setProgressDisplayIndeterminate(!item.determinate());
            if (item.determinate()) {
                progressDialog.updateProgress((float) item.totalBytesTransferred() / item.contentLength());
            } else {
                progressDialog.updateProgress(0);
            }
        });
        return HTTPUtil.retryableSend(httpClient, MutableRequest.GET(mirror.getIPDBUrl()), bodyHandler)
                .thenAccept(r -> {
                    if (r.statusCode() == 200) {
                        if (mirror.supportXzip()) {
                            try {
                                File tmp = File.createTempFile(databaseName, ".tmp");
                                try (XZCompressorInputStream gzipInputStream = new XZCompressorInputStream(new FileInputStream(r.body().toFile()));
                                     FileOutputStream fileOutputStream = new FileOutputStream(tmp)) {
                                    byte[] buffer = new byte[1024];
                                    int len;
                                    while ((len = gzipInputStream.read(buffer)) > 0) {
                                        fileOutputStream.write(buffer, 0, len);
                                    }
                                }
                                Files.move(tmp.toPath(), r.body(), StandardCopyOption.REPLACE_EXISTING);
                                log.info(tlUI(Lang.IPDB_UPDATE_SUCCESS, databaseName));
                                return;
                            } catch (IOException e) { // 下方统一进行处理
                                log.warn(tlUI(Lang.IPDB_UNGZIP_FAILED));
                            }
                        } else { // 直接就是原始文件
                            log.info(tlUI(Lang.IPDB_UPDATE_SUCCESS, databaseName));
                            return;
                        }
                    } else {
                        throw new IllegalStateException("Not a valid response");
                    }
                    if (!mirrorList.isEmpty()) { // 非 200 状态码 或者 gzip 解压出错
                        log.warn(tlUI(Lang.IPDB_RETRY_WITH_BACKUP_SOURCE));
                        downloadFile(mirrorList, path, databaseName);
                        return;
                    }
                    log.error(tlUI(Lang.IPDB_UPDATE_FAILED, databaseName, r.statusCode() + " - " + r.body()));
                })
                .exceptionally(e -> {
                    if (!mirrorList.isEmpty()) {
                        log.warn(tlUI(Lang.IPDB_RETRY_WITH_BACKUP_SOURCE));
                        return downloadFile(mirrorList, path, databaseName).join();
                    }
                    log.error(tlUI(Lang.IPDB_UPDATE_FAILED, databaseName, e.getMessage()), e);
                    File file = path.toFile();
                    if (file.exists()) {
                        file.delete(); // 删除下载不完整的文件
                    }
                    return null;
                }).whenComplete((r, e) -> progressDialog.close());
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
            this.provinceCode = Long.parseLong(provinceCode.toString());
            this.city = city;
            this.cityCode = Long.parseLong(cityCode.toString());
            this.districts = districts;
            this.districtsCode = Long.parseLong(districtsCode.toString());
        }
    }

    public static final class MaxMindNodeCache implements NodeCache {
        private final static Cache<CacheKey, DecodedValue> cache = CacheBuilder.newBuilder()
                .maximumSize(2000)
                .expireAfterAccess(1, TimeUnit.HOURS)
                .build();

        @SneakyThrows
        @Override
        public DecodedValue get(CacheKey cacheKey, Loader loader) throws IOException {
            return cache.get(cacheKey, () -> loader.load(cacheKey));
        }
    }

}
