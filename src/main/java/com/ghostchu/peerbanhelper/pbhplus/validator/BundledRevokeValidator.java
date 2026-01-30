package com.ghostchu.peerbanhelper.pbhplus.validator;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.pbhplus.bean.License;
import com.google.common.hash.Hashing;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import io.sentry.Sentry;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

@Slf4j
@Component
public class BundledRevokeValidator implements LicenseRevokeValidator {

    public BundledRevokeValidator() {

    }

    @Override
    public Collection<License> checkRevoked(@NotNull Collection<License> license) {
        return license.stream()
                .filter(this::isRevoked)
                .toList();
    }

    public boolean isRevoked(@NotNull License license) {
        try (Reader reader = new InputStreamReader(Main.class.getResourceAsStream("/revoked-licenses.csv"), StandardCharsets.UTF_8)) {
            HeaderColumnNameMappingStrategy<RevokedCSVItem> strategy = new HeaderColumnNameMappingStrategy<>();
            strategy.setType(RevokedCSVItem.class);
            CsvToBean<RevokedCSVItem> cb = new CsvToBeanBuilder<RevokedCSVItem>(reader)
                    .withMappingStrategy(strategy)
                    .withType(RevokedCSVItem.class)
                    .build();
            return cb.stream().anyMatch(item -> {
                boolean result = false;
                result = validateTrueShortCircuit(license.getLicenseTo(), item.getLicenseToHash(), result);
                result = validateTrueShortCircuit(license.getDescription(), item.getDescriptionHash(), result);
                result = validateTrueShortCircuit(license.getOrderId(), item.getOrderIdHash(), result);
                result = validateTrueShortCircuit(license.getPaymentOrderId(), item.getPaymentOrderIdHash(), result);
                result = validateTrueShortCircuit(license.getEmail(), item.getEmailHash(), result);
                return result;
            });
        } catch (IOException e) {
            log.debug("Unable to validate license {} to check if it revoked locally: {}", license.getLicenseTo(), e.getMessage());
            Sentry.captureException(e);
            return false;
        }
    }

    private boolean validateTrueShortCircuit(String unhashedValue, String compareTo, boolean anotherBoolean) {
        if (anotherBoolean) return true;
        if (unhashedValue == null) return false;
        String hashedValue = hash(unhashedValue);
        return compareTo.equalsIgnoreCase(hashedValue);
    }

    private String hash(String value) {
        if (value == null) return null;
        return Hashing.sha256().hashString(value, StandardCharsets.UTF_8).toString(); // 这里仅为示例，实际应返回哈希值
    }


    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class RevokedCSVItem {
        @CsvBindByName(column = "license_to_hash")
        @Nullable
        private String licenseToHash;
        @CsvBindByName(column = "description_hash")
        @Nullable
        private String descriptionHash;
        @CsvBindByName(column = "order_id_hash")
        @Nullable
        private String orderIdHash;
        @CsvBindByName(column = "payment_order_id_hash")
        @Nullable
        private String paymentOrderIdHash;
        @CsvBindByName(column = "email_hash")
        @Nullable
        private String emailHash;
    }

}
