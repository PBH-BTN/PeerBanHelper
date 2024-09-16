package com.ghostchu.peerbanhelper.ipdb;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.maxmind.db.MaxMindDbConstructor;
import com.maxmind.db.MaxMindDbParameter;
import com.maxmind.db.Reader;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Location;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class IPDB implements AutoCloseable {
    private final Cache<InetAddress, IPGeoData> MINI_CACHE = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(5, TimeUnit.SECONDS)
            .build();
    private final File dataFolder;
    private final long updateInterval = 2592000000L; // 30天
    private final String accountId;
    private final String licenseKey;
    private final File directory;
    private final File mmdbCityFile;
    private final File mmdbASNFile;
    private final boolean autoUpdate;
    private final String userAgent;
    private final File mmdbGeoCNFile;
    private Methanol httpClient;
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
        setupHttpClient();
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
        try {
            return MINI_CACHE.get(address, () -> {
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
            });
        } catch (ExecutionException e) {
            return new IPGeoData();
        }

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
        } catch (Exception e) {
            log.error("Unable to execute IPDB query", e);
        }
    }

    private IPGeoData.NetworkData queryNetwork(InetAddress address) {
        IPGeoData.NetworkData networkData = new IPGeoData.NetworkData();
        try {
            AsnResponse asnResponse = mmdbASN.asn(address);
            networkData.setIsp(asnResponse.getAutonomousSystemOrganization());
            networkData.setNetType(null);
        } catch (Exception ignored) {
        }
        return networkData;
    }


    private IPGeoData.CityData queryCity(InetAddress address) {
        IPGeoData.CityData cityData = new IPGeoData.CityData();
        IPGeoData.CityData.LocationData locationData = new IPGeoData.CityData.LocationData();
        try {
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
        } catch (Exception e) {
        }
        return cityData;
    }

    private IPGeoData.CountryData queryCountry(InetAddress address) {
        IPGeoData.CountryData countryData = new IPGeoData.CountryData();
        try {
            CountryResponse countryResponse = mmdbCity.country(address);
            Country country = countryResponse.getCountry();
            countryData.setIso(country.getIsoCode());
            String countryRegionName = country.getName();
            // 对 TW,HK,MO 后处理，偷个懒
            if (languageTag.getFirst().equals("zh-CN") && (country.getIsoCode().equals("TW") || country.getIsoCode().equals("HK") || country.getIsoCode().equalsIgnoreCase("MO"))) {
                countryRegionName = "中国" + countryRegionName;
            }
            countryData.setName(countryRegionName);
        } catch (Exception ignored) {
        }
        return countryData;
    }


    private IPGeoData.ASData queryAS(InetAddress address) {
        IPGeoData.ASData asData = new IPGeoData.ASData();
        try {
            AsnResponse asnResponse = mmdbASN.asn(address);
            IPGeoData.ASData.ASNetwork network = new IPGeoData.ASData.ASNetwork();
            network.setPrefixLength(asnResponse.getNetwork().getPrefixLength());
            network.setIpAddress(asnResponse.getNetwork().getNetworkAddress().getHostAddress());
            asData.setNumber(asnResponse.getAutonomousSystemNumber());
            asData.setOrganization(asnResponse.getAutonomousSystemOrganization());
            asData.setIpAddress(asnResponse.getIpAddress());
            asData.setNetwork(network);
        } catch (Exception ignored) {
        }
        return asData;
    }

    private void updateGeoCN(File mmdbGeoCNFile) throws IOException {
        log.info(tlUI(Lang.IPDB_UPDATING, "GeoCN (github.com/ljxi/GeoCN)"));
        MutableRequest main = MutableRequest.GET("https://github.com/ljxi/GeoCN/releases/download/Latest/GeoCN.mmdb");
        MutableRequest backup = MutableRequest.GET("https://pbh-static.ghostchu.com/ipdb/GeoCN.mmdb");
        Path tmp = Files.createTempFile("GeoCN", ".mmdb");
        downloadFile(main, backup, tmp, "GeoCN").join();
        if (!tmp.toFile().exists()) {
            throw new IllegalStateException("Download mmdb database failed!");
        }
        Files.move(tmp, mmdbGeoCNFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }


    private void loadMMDB() throws IOException {
        this.languageTag = List.of(Main.DEF_LOCALE, "en");
        this.mmdbCity = new DatabaseReader.Builder(mmdbCityFile)
                .locales(List.of(Main.DEF_LOCALE, "en")).build();
        this.mmdbASN = new DatabaseReader.Builder(mmdbASNFile)
                .locales(List.of(Main.DEF_LOCALE, "en")).build();
        this.geoCN = new Reader(mmdbGeoCNFile);
    }

    private void updateMMDB(String databaseName, File target) throws IOException {
        log.info(tlUI(Lang.IPDB_UPDATING, databaseName));
        MutableRequest main = MutableRequest.GET("https://github.com/P3TERX/GeoLite.mmdb/raw/download/" + databaseName + ".mmdb");
        MutableRequest backup = MutableRequest.GET("https://pbh-static.ghostchu.com/ipdb/" + databaseName + ".mmdb");
        Path tmp = Files.createTempFile(databaseName, ".mmdb");
        downloadFile(main, backup, tmp, databaseName).join();
        if (!tmp.toFile().exists()) {
            if (isMmdbNeverDownloaded(target)) {
                throw new IllegalStateException("Download mmdb database failed!");
            } else {
                log.warn(tlUI(Lang.IPDB_EXISTS_UPDATE_FAILED, databaseName));
            }
        }
        Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void setupHttpClient() {
        this.httpClient = Methanol
                .newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .userAgent(userAgent)
                .defaultHeader("Accept-Encoding", "gzip,deflate")
                .connectTimeout(Duration.of(15, ChronoUnit.SECONDS))
                .headersTimeout(Duration.of(15, ChronoUnit.SECONDS))
                .readTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .requestTimeout(Duration.of(2, ChronoUnit.MINUTES))
                .authenticator(new Authenticator() {
                    @Override
                    public PasswordAuthentication requestPasswordAuthenticationInstance(String host, InetAddress addr, int port, String protocol, String prompt, String scheme, URL url, RequestorType reqType) {
                        return new PasswordAuthentication(accountId, licenseKey.toCharArray());
                    }
                })
                .build();
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

    private CompletableFuture<Void> downloadFile(MutableRequest req, MutableRequest backupReq, Path path, String databaseName) {
        return HTTPUtil.retryableSendProgressTracking(httpClient, req, HttpResponse.BodyHandlers.ofFile(path))
                .thenAccept(r -> {
                    if (r.statusCode() != 200) {
                        if (backupReq != null) {
                            log.warn(tlUI(Lang.IPDB_RETRY_WITH_BACKUP_SOURCE));
                            downloadFile(backupReq, null, path, databaseName);
                            return;
                        }
                        log.error(tlUI(Lang.IPDB_UPDATE_FAILED, databaseName, r.statusCode() + " - " + r.body()));
                    } else {
                        log.info(tlUI(Lang.IPDB_UPDATE_SUCCESS, databaseName));
                    }
                })
                .exceptionally(e -> {
                    if (backupReq != null) {
                        log.warn(tlUI(Lang.IPDB_RETRY_WITH_BACKUP_SOURCE));
                        return downloadFile(backupReq, null, path, databaseName).join();
                    }
                    log.error(tlUI(Lang.IPDB_UPDATE_FAILED, databaseName, e.getMessage()), e);
                    File file = path.toFile();
                    if (file.exists()) {
                        file.delete(); // 删除下载不完整的文件
                    }
                    return null;
                });
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

}
