package com.ghostchu.peerbanhelper.databasent;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class MultiDbExplainInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];
        BoundSql boundSql = ms.getBoundSql(parameter);

        if (ms.getSqlCommandType() == SqlCommandType.SELECT) {
            // 获取数据库连接（注意：不要关闭这个 connection，它由 MyBatis 管理）
            Connection connection = ms.getConfiguration().getEnvironment().getDataSource().getConnection();
            try {
                String dbProductName = connection.getMetaData().getDatabaseProductName().toLowerCase();
                executeExplain(ms, parameter, boundSql, connection, dbProductName);
            } finally {
                // 必须手动关闭从 DataSource 获取的临时连接
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            }
        }
        return invocation.proceed();
    }

    private void executeExplain(MappedStatement ms, Object parameter, BoundSql boundSql, Connection conn, String dbType) {
        String explainSql = "EXPLAIN " + boundSql.getSql();

        try (PreparedStatement ps = conn.prepareStatement(explainSql)) {
            // 修复点：使用 MyBatis 提供的默认参数处理器
            ParameterHandler ph = ms.getConfiguration().newParameterHandler(ms, parameter, boundSql);
            ph.setParameters(ps);

            try (ResultSet rs = ps.executeQuery()) {
                analyzeResult(rs, dbType, boundSql.getSql());
            }
        } catch (Exception e) {
            System.err.println("Explain 执行跳过: " + e.getMessage());
        }
    }

    private void analyzeResult(ResultSet rs, String dbType, String sql) throws SQLException {
        if (!rs.next()) return;

        if (dbType.contains("mysql")) {
            String type = rs.getString("type");
            // MySQL: ALL 代表全表扫描，index 代表全索引扫描
            if ("ALL".equalsIgnoreCase(type)) {
                System.err.println("⚠️ [MySQL 风险] 全表扫描: " + sql);
            }
        } else if (dbType.contains("postgresql")) {
            String plan = rs.getString(1);
            if (plan.toLowerCase().contains("seq scan")) {
                System.err.println("⚠️ [Postgres 风险] 顺序扫描: " + sql);
            }
        } else if (dbType.contains("h2")) {
            String plan = rs.getString(1);
            // H2 的执行计划中，如果没有出现 "INDEX"，通常意味着全表扫描
            if (!plan.contains("INDEX") && plan.contains("/*")) {
                System.err.println("⚠️ [H2 风险] 可能的全表扫描: " + sql);
            }
        }
    }
}