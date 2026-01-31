package com.ghostchu.peerbanhelper.pbhplus.validator;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.pbhplus.bean.License;
import com.google.common.hash.Hashing;
import io.sentry.Sentry;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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
        try (InputStream is = Main.class.getResourceAsStream("/revoked-licenses.csv")) {
            if (is == null) {
                return false;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String headerLine = reader.readLine();
                if (headerLine == null) {
                    return false;
                }
                String[] headers = headerLine.split(",");
                Map<String, Integer> headerMap = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    headerMap.put(headers[i].trim(), i);
                }

                return reader.lines().anyMatch(line -> {
                    if (line.trim().isEmpty()) {
                        return false;
                    }
                    String[] columns = line.split(",", -1);
                    RevokedCSVItem item = new RevokedCSVItem();
                    item.setLicenseToHash(getColumnValue(columns, headerMap, "license_to_hash"));
                    item.setDescriptionHash(getColumnValue(columns, headerMap, "description_hash"));
                    item.setOrderIdHash(getColumnValue(columns, headerMap, "order_id_hash"));
                    item.setPaymentOrderIdHash(getColumnValue(columns, headerMap, "payment_order_id_hash"));
                    item.setEmailHash(getColumnValue(columns, headerMap, "email_hash"));

                    boolean result = false;
                    result = validateTrueShortCircuit(license.getLicenseTo(), item.getLicenseToHash(), result);
                    result = validateTrueShortCircuit(license.getDescription(), item.getDescriptionHash(), result);
                    result = validateTrueShortCircuit(license.getOrderId(), item.getOrderIdHash(), result);
                    result = validateTrueShortCircuit(license.getPaymentOrderId(), item.getPaymentOrderIdHash(), result);
                    result = validateTrueShortCircuit(license.getEmail(), item.getEmailHash(), result);
                    return result;
                });
            }
        } catch (IOException e) {
            log.debug("Unable to validate license {} to check if it revoked locally: {}", license.getLicenseTo(), e.getMessage());
            Sentry.captureException(e);
            return false;
        }
    }

    private String getColumnValue(String[] columns, Map<String, Integer> headerMap, String columnName) {
        Integer index = headerMap.get(columnName);
        if (index != null && index < columns.length) {
            String value = columns[index].trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            return value.isEmpty() ? null : value;
        }
        return null;
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
        @Nullable
        private String licenseToHash;
        @Nullable
        private String descriptionHash;
        @Nullable
        private String orderIdHash;
        @Nullable
        private String paymentOrderIdHash;
        @Nullable
        private String emailHash;
    }

}
