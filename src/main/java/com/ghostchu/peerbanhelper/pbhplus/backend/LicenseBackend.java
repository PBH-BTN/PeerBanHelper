package com.ghostchu.peerbanhelper.pbhplus.backend;

import com.ghostchu.peerbanhelper.pbhplus.bean.License;
import com.ghostchu.peerbanhelper.pbhplus.data.LicenseStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

public interface LicenseBackend {

    void setLicenses(@NotNull Map<String, License> in);

    @NotNull Map<String, License> getLicensesMap();

    Collection<License> getLicenses();

    @NotNull LicenseStatus getLicenseStatus(@NotNull License license);

    @NotNull Collection<License> getValidLicenses();

    boolean isFeatureEnabled(@NotNull String feature);

    Collection<String> getAllFeatures();
}
