package com.ghostchu.peerbanhelper.util;

import com.baomidou.mybatisplus.core.toolkit.sql.SqlInjectionUtils;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

@Slf4j
public class SQLHelper {
    private final static Pattern SAFE_FIELD_NAME = Pattern.compile("^[a-zA-Z0-9_.]+$");

    public static String checkSafeFieldName(@Nullable String fieldName) {
        if(!isSafeFieldName(fieldName)) {
            throw new IllegalArgumentException("Detected non-valid field name pattern in input: " + fieldName);
        }
        return fieldName;
    }

    public static boolean isSafeFieldName(@Nullable String fieldName) {
        if (fieldName == null) return false;
        return SAFE_FIELD_NAME.matcher(fieldName).matches();
    }

    @Nullable
    public static String checkSQLInjection(@Nullable String sql) {
        if (sql == null) return null;
        if (SqlInjectionUtils.check(sql)) {
            log.error("Alert: Detected user input SQL injection pattern: {}, check if your webapi exposed to public Internet.", sql);
            throw new IllegalArgumentException("Detected dangerous SQL injection pattern in input");
        }
        return sql;
    }
}
