package com.ghostchu.peerbanhelper.databasent;

import com.ghostchu.peerbanhelper.configuration.DatabaseDriverConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Component
@Slf4j
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class MultiDbExplainInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler handler = (StatementHandler) invocation.getTarget();
        BoundSql boundSql = handler.getBoundSql();
        String originalSql = boundSql.getSql();

        // 仅处理 SELECT 语句，避开 INSERT/UPDATE/DELETE
        if (originalSql.trim().toUpperCase().startsWith("SELECT")) {
            // 从当前的 Invocation 中获取 Connection
            Connection connection = (Connection) invocation.getArgs()[0];

            // 获取当前驱动类型（通过你配置类中的静态变量或注入）
            var driver = DatabaseDriverConfig.databaseDriver;
            if (driver != null) {
                runExplain(connection, handler, boundSql, driver);
            }
        }

        return invocation.proceed();
    }

    private void runExplain(Connection conn, StatementHandler handler, BoundSql boundSql, DatabaseDriver driver) {
        String explainSql = "EXPLAIN " + boundSql.getSql();

        // 注意：这里使用原有的 Connection，不要关闭它，否则后续主查询会失败
        try (PreparedStatement ps = conn.prepareStatement(explainSql)) {
            // 绑定参数
            handler.getParameterHandler().setParameters(ps);

            try (ResultSet rs = ps.executeQuery()) {
                analyzeResult(rs, driver, boundSql.getSql());
            }
        } catch (Exception e) {
            // 静默处理：部分特殊语法（如 UNION）在某些数据库下 EXPLAIN 可能会报错
            log.trace("Explain failed for SQL: {}", explainSql, e);
        }
    }

    private void analyzeResult(ResultSet rs, DatabaseDriver driver, String sql) throws Exception {
        if (!rs.next()) return;

        DatabaseType dbType = driver.getType();

        if (dbType == DatabaseType.MYSQL) {
            String type = rs.getString("type");
            if ("ALL".equalsIgnoreCase(type)) {
                log.warn("🚨 [MySQL 性能风险] 检测到全表扫描！\nSQL: {}", formatSql(sql));
            }
        } else if (dbType == DatabaseType.H2) {
            String plan = rs.getString(1);

            // --- 核心逻辑优化 ---
            // 1. 如果包含 "SCAN()" 且不包含任何索引引用，则是全表扫描
            // 2. 如果 plan 包含 "/*" 且里面有索引名（通常以 IDX_ 或 PRIMARY_KEY 开头），则是索引扫描

            boolean hasIndexIndicator = plan.contains("INDEX")
                    || plan.contains("PRIMARY_KEY")
                    || plan.contains("IDX_") // 匹配常见的索引命名规范
                    || (plan.contains("/*") && !plan.contains(".SCAN()")); // 注释块内不是 SCAN 往往就是索引

            // 只有明确出现 SCAN 且没被判定为有索引时，才报错
            if (plan.contains(".SCAN()") && !hasIndexIndicator) {
                log.warn("🚨 [H2 性能风险] 检测到全表扫描！\nPLAN: {}\nSQL: {}", plan.trim(), formatSql(sql));
            }
        }
    }

    private String formatSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}