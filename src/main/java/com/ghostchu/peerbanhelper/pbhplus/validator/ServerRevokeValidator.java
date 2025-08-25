package com.ghostchu.peerbanhelper.pbhplus.validator;

import com.ghostchu.peerbanhelper.pbhplus.bean.License;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.common.hash.Hashing;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ServerRevokeValidator implements LicenseRevokeValidator {

    private final HTTPUtil httpUtil;
    private final Path cacheDirectory;
    private static final long CACHE_DURATION_HOURS = 24;

    public ServerRevokeValidator(HTTPUtil httpUtil) {
        this.httpUtil = httpUtil;
        this.cacheDirectory = Paths.get("data", "cache", "license-revoke");
        try {
            Files.createDirectories(cacheDirectory);
        } catch (IOException e) {
            log.warn("Failed to create cache directory: {}", e.getMessage());
        }
    }

    @Override
    public Collection<License> checkRevoked(@NotNull Collection<License> licenses) {
        List<License> revoked = new ArrayList<>();
        var httpClient = httpUtil.newBuilder().callTimeout(15, TimeUnit.SECONDS).build();
        for (License license : licenses) {
            if (checkRevoked(httpClient, license))
                revoked.add(license);
        }
        return revoked;
    }

    private boolean checkRevoked(OkHttpClient httpClient, License license) {
        String licenseHash = generateLicenseHash(license);
        // Check cache first
        CacheEntry cachedResult = getCachedResult(licenseHash);
        if (cachedResult != null && !isCacheExpired(cachedResult.getTimestamp())) {
            log.debug("Using cached result for license {}: {}", license.getLicenseTo(), cachedResult.isRevoked());
            return cachedResult.isRevoked();
        }
        // Perform actual check
        boolean revoked = performRevokeCheck(httpClient, license);
        // Cache the result
        cacheResult(licenseHash, revoked);
        return revoked;
    }

    private boolean performRevokeCheck(OkHttpClient httpClient, License license) {
        var urlBuilder = new HttpUrl.Builder()
                .scheme("https")
                .host("api.pbh-btn.com")
                .addPathSegment("peerbanhelper")
                .addPathSegment("v1")
                .addPathSegment("licenses")
                .addPathSegment("checkRevoke");
        if (license.getLicenseTo() != null)
            urlBuilder.addQueryParameter("licenseToHash", hash(license.getLicenseTo()));
        if (license.getDescription() != null)
            urlBuilder.addQueryParameter("descriptionHash", hash(license.getDescription()));
        if (license.getOrderId() != null)
            urlBuilder.addQueryParameter("orderIdHash", hash(license.getOrderId()));
        if (license.getPaymentOrderId() != null)
            urlBuilder.addQueryParameter("paymentOrderIdHash", hash(license.getPaymentOrderId()));
        if (license.getEmail() != null)
            urlBuilder.addQueryParameter("emailHash", hash(license.getEmail()));
        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.debug("Failed to check license revoke for {}: http code: {}, body: {}", license.getLicenseTo(), response.code(), response.body());
                return false;
            }
            if (response.code() == 204) {
                log.debug("License {} is not revoked", license.getLicenseTo());
                return false;
            }
            var result = JsonUtil.standard().fromJson(response.body().charStream(), CheckResult.class);
            return result.isRevoked();
        } catch (Exception e) {
            log.debug("Failed to check license revoke for {}: {}", license.getLicenseTo(), e.getMessage());
            return false;
        }
    }

    private String hash(String value) {
        if (value == null) return null;
        return Hashing.sha256().hashString(value, StandardCharsets.UTF_8).toString(); // 这里仅为示例，实际应返回哈希值
    }

    private String generateLicenseHash(License license) {
        StringBuilder sb = new StringBuilder();
        if (license.getLicenseTo() != null) sb.append(license.getLicenseTo());
        if (license.getDescription() != null) sb.append(license.getDescription());
        if (license.getOrderId() != null) sb.append(license.getOrderId());
        if (license.getPaymentOrderId() != null) sb.append(license.getPaymentOrderId());
        if (license.getEmail() != null) sb.append(license.getEmail());
        return Hashing.sha256().hashString(sb.toString(), StandardCharsets.UTF_8).toString();
    }

    private CacheEntry getCachedResult(String licenseHash) {
        Path cacheFile = cacheDirectory.resolve(licenseHash + ".json");
        if (!Files.exists(cacheFile)) {
            return null;
        }

        try {
            String content = Files.readString(cacheFile, StandardCharsets.UTF_8);
            return JsonUtil.standard().fromJson(content, CacheEntry.class);
        } catch (Exception e) {
            log.debug("Failed to read cache file {}: {}", cacheFile, e.getMessage());
            return null;
        }
    }

    private void cacheResult(String licenseHash, boolean revoked) {
        Path cacheFile = cacheDirectory.resolve(licenseHash + ".json");
        CacheEntry entry = new CacheEntry(revoked, Instant.now().toEpochMilli());

        try {
            String content = JsonUtil.standard().toJson(entry);
            Files.writeString(cacheFile, content, StandardCharsets.UTF_8);
            log.debug("Cached result for license hash {}: {}", licenseHash, revoked);
        } catch (Exception e) {
            log.debug("Failed to cache result for license hash {}: {}", licenseHash, e.getMessage());
        }
    }

    private boolean isCacheExpired(long timestamp) {
        long now = Instant.now().toEpochMilli();
        long cacheAge = now - timestamp;
        long maxAge = CACHE_DURATION_HOURS * 60 * 60 * 1000; // 24 hours in milliseconds
        return cacheAge > maxAge;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class CheckResult {
        private boolean revoked;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class CacheEntry {
        private boolean revoked;
        private long timestamp;
    }
}
