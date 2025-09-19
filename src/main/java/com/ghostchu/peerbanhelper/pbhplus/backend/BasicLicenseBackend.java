package com.ghostchu.peerbanhelper.pbhplus.backend;

import com.ghostchu.peerbanhelper.pbhplus.bean.License;
import com.ghostchu.peerbanhelper.pbhplus.data.LicenseStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

@Component
public class BasicLicenseBackend implements LicenseBackend {
    protected final Map<String, License> licenses = Collections.synchronizedMap(new LinkedHashMap<>());

    @Override
    public void setLicenses(@NotNull Map<String, License> in) {
        synchronized (licenses) {
            this.licenses.clear();
            this.licenses.putAll(in);
        }
    }

    @Override
    public @NotNull Map<String, License> getLicensesMap() {
        return Collections.unmodifiableMap(licenses);
    }

    @Override
    public Collection<License> getLicenses() {
        return licenses.values();
    }

    @Override
    public @NotNull LicenseStatus getLicenseStatus(@NotNull License license) {
        if (license.getExpireAt() < System.currentTimeMillis()) {
            return LicenseStatus.EXPIRED;
        }
        if (license.getStartAt() > System.currentTimeMillis()) {
            return LicenseStatus.NOT_STARTED;
        }
        return LicenseStatus.VALID;
    }

    @Override
    public @NotNull Collection<License> getValidLicenses() {
        return getLicenses().stream()
                .filter(license -> getLicenseStatus(license) == LicenseStatus.VALID)
                .toList();
    }

    @Override
    public boolean isFeatureEnabled(@NotNull String feature) {
        return getValidLicenses().stream()
                .anyMatch(license -> license.getFeatures() != null
                        && license.getFeatures().contains(feature));
    }

    @Override
    public Collection<String> getAllFeatures() {
        return getValidLicenses().stream()
                .flatMap(license -> license.getFeatures() != null ? license.getFeatures().stream() : Stream.empty())
                .distinct()
                .toList();
    }
}
