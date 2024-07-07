package com.ghostchu.peerbanhelper.ipdb;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import com.ice.tar.TarEntry;
import com.ice.tar.TarInputStream;
import com.maxmind.db.MaxMindDbConstructor;
import com.maxmind.db.MaxMindDbParameter;
import com.maxmind.db.Reader;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Location;
import lombok.Cleanup;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
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
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;

@Slf4j
public class IPDB implements AutoCloseable {
    private final File dataFolder;
    private final long updateInterval = 86400000L; // 30天
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

    public IPGeoData query(InetAddress address) throws IOException, GeoIp2Exception {
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
            // City Data
            IPGeoData.CityData cityResponse = Objects.requireNonNullElse(geoData.getCity(), new IPGeoData.CityData());
            String cityName = (cnLookupResult.getProvince() + " " + cnLookupResult.getCity() + " " + cnLookupResult.getDistricts()).trim();
            if (!cityName.isBlank()) {
                cityResponse.setName(cityName);
            }
            Integer code = null;
            if (cnLookupResult.getProvinceCode() != null) {
                code = cnLookupResult.getProvinceCode();
            }
            if (cnLookupResult.getCityCode() != null) {
                code = cnLookupResult.getCityCode();
            }
            if (cnLookupResult.getDistrictsCode() != null) {
                code = cnLookupResult.getDistrictsCode();
            }
            cityResponse.setIso(Long.parseLong("86" + code));
            geoData.setCity(cityResponse);
            // Network Data
            IPGeoData.NetworkData networkData = Objects.requireNonNullElse(geoData.getNetwork(), new IPGeoData.NetworkData());
            if (cnLookupResult.getIsp() != null && !cnLookupResult.getIsp().isBlank()) {
                networkData.setIsp(cnLookupResult.getIsp());
            }
            if (cnLookupResult.getNet() != null && !cnLookupResult.getNet().isBlank()) {
                networkData.setNetType(cnLookupResult.getNet());
            }
            geoData.setNetwork(networkData);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private IPGeoData.NetworkData queryNetwork(InetAddress address) {
        IPGeoData.NetworkData networkData = new IPGeoData.NetworkData();
        try {
            AsnResponse asnResponse = mmdbASN.asn(address);
            networkData.setIsp(asnResponse.getAutonomousSystemOrganization());
            networkData.setNetType("Unknown");
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
            countryData.setName(country.getName());
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
        log.info(Lang.IPDB_UPDATING, "GeoCN (github.com/ljxi/GeoCN)");
        MutableRequest request = MutableRequest.GET("https://github.com/ljxi/GeoCN/releases/download/Latest/GeoCN.mmdb");
        Path tmp = Files.createTempFile("GeoCN", ".mmdb");
        downloadFile(request, tmp, "GeoCN").join();
        if (!tmp.toFile().exists()) {
            throw new IllegalStateException("Download mmdb database failed!");
        }
        Files.move(tmp, mmdbGeoCNFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void loadMMDB() throws IOException {
        this.mmdbCity = new DatabaseReader.Builder(mmdbCityFile)
                .locales(List.of(Locale.getDefault().toLanguageTag(), "en")).build();
        this.mmdbASN = new DatabaseReader.Builder(mmdbASNFile)
                .locales(List.of(Locale.getDefault().toLanguageTag(), "en")).build();
        this.geoCN = new Reader(mmdbGeoCNFile);
    }

    private void updateMMDB(String databaseName, File target) throws IOException {
        log.info(Lang.IPDB_UPDATING, databaseName);
        MutableRequest request = MutableRequest.GET("https://download.maxmind.com/geoip/databases/" + databaseName + "/download?suffix=tar.gz");
        Path tmp = Files.createTempFile("ipdb-mmdb-archive", ".tar.gz");
        downloadFile(request, tmp, databaseName).join();
        if (!tmp.toFile().exists()) {
            throw new IllegalStateException("Download mmdb database failed!");
        }
        boolean found = false;
        @Cleanup
        InputStream gzipIn = new GZIPInputStream(new FileInputStream(tmp.toFile()));
        @Cleanup
        TarInputStream tarInputStream = new TarInputStream(gzipIn);
        String filename;
        TarEntry entry;
        while ((entry = tarInputStream.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
                filename = entry.getName();
                if (filename.substring(filename.length() - 5).equalsIgnoreCase(".mmdb")) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            throw new IllegalStateException("Except an .mmdb file inside Maxmind archive");
        }
        Path path = Files.createTempFile("ipdb-extracted", ".mmdb");
        File out = path.toFile();
        try (FileOutputStream outputStream = new FileOutputStream(out)) {
            byte[] buffer = new byte[1024];
            int length = tarInputStream.read(buffer);
            while (length >= 0) {
                outputStream.write(buffer, 0, length);
                length = tarInputStream.read(buffer);
            }
        }
        Files.move(path, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void setupHttpClient() {
        this.httpClient = Methanol
                .newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .userAgent(userAgent)
                .connectTimeout(Duration.of(15, ChronoUnit.SECONDS))
                .headersTimeout(Duration.of(15, ChronoUnit.SECONDS))
                .authenticator(new Authenticator() {
                    @Override
                    public PasswordAuthentication requestPasswordAuthenticationInstance(String host, InetAddress addr, int port, String protocol, String prompt, String scheme, URL url, RequestorType reqType) {
                        return new PasswordAuthentication(accountId, licenseKey.toCharArray());
                    }
                })
                .build();
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

    private CompletableFuture<Void> downloadFile(MutableRequest req, Path path, String databaseName) {
        return HTTPUtil.retryableSendProgressTracking(httpClient, req, HttpResponse.BodyHandlers.ofFile(path))
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
        private final Integer provinceCode;
        private final String city;
        private final Integer cityCode;
        private final String districts;
        private final Integer districtsCode;

        @MaxMindDbConstructor
        public CNLookupResult(
                @MaxMindDbParameter(name = "isp") String isp,
                @MaxMindDbParameter(name = "net") String net,
                @MaxMindDbParameter(name = "province") String province,
                @MaxMindDbParameter(name = "provinceCode") Integer provinceCode,
                @MaxMindDbParameter(name = "city") String city,
                @MaxMindDbParameter(name = "cityCode") Integer cityCode,
                @MaxMindDbParameter(name = "districts") String districts,
                @MaxMindDbParameter(name = "districtsCode") Integer districtsCode
        ) {
            this.isp = isp;
            this.net = net;
            this.province = province;
            this.provinceCode = provinceCode;
            this.city = city;
            this.cityCode = cityCode;
            this.districts = districts;
            this.districtsCode = districtsCode;
        }
    }

}
