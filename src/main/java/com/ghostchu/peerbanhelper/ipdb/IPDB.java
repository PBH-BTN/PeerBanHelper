package com.ghostchu.peerbanhelper.ipdb;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import com.ice.tar.TarEntry;
import com.ice.tar.TarInputStream;
import com.maxmind.geoip2.DatabaseReader;
import lombok.Cleanup;
import lombok.Getter;
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
    private Methanol httpClient;
    @Getter
    private DatabaseReader mmdbCity;
    @Getter
    private DatabaseReader mmdbASN;

    public IPDB(File dataFolder, String accountId, String licenseKey, String databaseCity, String databaseASN, boolean autoUpdate, String userAgent) throws IllegalArgumentException, IOException {
        this.dataFolder = dataFolder;
        this.accountId = accountId;
        this.licenseKey = licenseKey;
        this.directory = new File(dataFolder, "geoip");
        this.directory.mkdirs();
        this.mmdbCityFile = new File(directory, "GeoIP-City.mmdb");
        this.mmdbASNFile = new File(directory, "GeoIP-ASN.mmdb");
        this.autoUpdate = autoUpdate;
        this.userAgent = userAgent;
        setupHttpClient();
        if (needUpdateMMDB(mmdbCityFile)) {
            updateMMDB(databaseCity, mmdbCityFile);
        }
        if (needUpdateMMDB(mmdbASNFile)) {
            updateMMDB(databaseASN, mmdbASNFile);
        }
        loadMMDB();
    }

    private void loadMMDB() throws IOException {
        this.mmdbCity = new DatabaseReader.Builder(mmdbCityFile)
                .locales(List.of(Locale.getDefault().toLanguageTag(), "en")).build();
        this.mmdbASN = new DatabaseReader.Builder(mmdbASNFile)
                .locales(List.of(Locale.getDefault().toLanguageTag(), "en")).build();
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
    }

}
