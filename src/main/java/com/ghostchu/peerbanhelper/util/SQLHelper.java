package com.ghostchu.peerbanhelper.util;

import com.baomidou.mybatisplus.core.toolkit.sql.SqlInjectionUtils;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class SQLHelper {
    @Nullable
    public static String checkSQLInjectionAndReturnSafe(@Nullable String sql) {
        if (sql == null) return null;
        if (SqlInjectionUtils.check(sql)) {
            log.error("Alert: Detected user input SQL injection pattern: {}, check if your webapi exposed to public Internet.", sql);
            throw new IllegalArgumentException("Detected dangerous SQL injection pattern in input");
        }
        return sql;
    }
}
