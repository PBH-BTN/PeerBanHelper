package com.ghostchu.peerbanhelper.pbhplus.backend;

import com.ghostchu.peerbanhelper.pbhplus.bean.License;
import com.ghostchu.peerbanhelper.pbhplus.data.LicenseStatus;
import com.ghostchu.peerbanhelper.pbhplus.validator.LicenseRevokeValidator;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.spotify.futures.CompletableFutures;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RevolvableLicenseBackend extends BasicLicenseBackend {
    private final Set<License> revokedLicenses = Collections.synchronizedSet(new LinkedHashSet<>());
    private final List<LicenseRevokeValidator> revokeValidators;

    public RevolvableLicenseBackend(List<LicenseRevokeValidator> revokeValidators) {
        this.revokeValidators = revokeValidators;
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::checkRevokedLicenses, 0, 1, TimeUnit.DAYS);
    }

    private synchronized void checkRevokedLicenses() {
        try {
            Set<License> revoked = Collections.synchronizedSet(new HashSet<>());
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (LicenseRevokeValidator validator : revokeValidators) {
                futures.add(CompletableFuture.runAsync(() -> {
                    revoked.addAll(validator.checkRevoked(licenses.values()));
                }));
            }
            CompletableFutures.allAsList(futures).join();
            synchronized (revokedLicenses) {
                revokedLicenses.clear();
                revokedLicenses.addAll(revoked);
            }
        } catch (Exception e) {
            log.debug("Error checking revoked licenses: {}", e.getMessage(), e);
            Sentry.captureException(e);
        }
    }

    private synchronized boolean checkRevokedLicense(License license) {
        try {
            Set<License> revoked = Collections.synchronizedSet(new HashSet<>());
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (LicenseRevokeValidator validator : revokeValidators) {
                futures.add(CompletableFuture.runAsync(() -> {
                    revoked.addAll(validator.checkRevoked(licenses.values()));
                }));
            }
            CompletableFutures.allAsList(futures).join();
            synchronized (revokedLicenses) {
                revokedLicenses.clear();
                revokedLicenses.addAll(revoked);
            }
            return revoked.contains(license);
        } catch (Exception e) {
            log.debug("Error checking revoked licenses: {}", e.getMessage(), e);
            Sentry.captureException(e);
            return false;
        }
    }


    @Override
    public @NotNull LicenseStatus getLicenseStatus(@NotNull License license) {
        if (revokedLicenses.contains(license)) {
            return LicenseStatus.REVOKED;
        }
        if (!licenses.containsValue(license)) {
            if (checkRevokedLicense(license)) {
                return LicenseStatus.REVOKED;
            }
        }
        return super.getLicenseStatus(license);
    }

    @Override
    public void setLicenses(@NotNull Map<String, License> in) {
        super.setLicenses(in);

    }
}