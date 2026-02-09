package com.ghostchu.peerbanhelper.databasent.driver.postgres;

import com.alibaba.druid.pool.DruidDataSource;
import com.ghostchu.peerbanhelper.databasent.DatabaseType;
import com.ghostchu.peerbanhelper.databasent.driver.AbstractDatabaseDriver;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.SQLException;

public class PostgresDatabaseDriver extends AbstractDatabaseDriver {
    private final ConfigurationSection section;
    private final DruidDataSource dataSource;

    public PostgresDatabaseDriver(@NotNull ConfigurationSection section) throws IOException {
        super();
        this.section = section;
        
        String host = section.getString("host");
        int port = section.getInt("port");
        String database = section.getString("database");
        String username = section.getString("username");
        String password = section.getString("password");

        DruidDataSource druidDataSource = new DruidDataSource();
        druidDataSource.setUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
        druidDataSource.setUsername(username);
        druidDataSource.setPassword(password);
        druidDataSource.setDriverClassName("org.postgresql.Driver");
        druidDataSource.setMaxActive(10);
        druidDataSource.setMinIdle(1);
        druidDataSource.setMaxWait(30000);
        druidDataSource.setTimeBetweenEvictionRunsMillis(600000);

        // 连接池验证配置
        druidDataSource.setValidationQuery("SELECT 1");
        druidDataSource.setTestWhileIdle(true);
        druidDataSource.setTestOnBorrow(true);
        druidDataSource.setTestOnReturn(false);

        // 启用 fairQueuing FIFO - 使用公平锁
        druidDataSource.setUseUnfairLock(false);

        // PostgreSQL-specific optimizations
        druidDataSource.addConnectionProperty("ApplicationName", "PeerBanHelper");
        druidDataSource.addConnectionProperty("tcpKeepAlive", "true");
        druidDataSource.addConnectionProperty("reWriteBatchedInserts", "true"); // Improve batch insert performance

        // 启用 Druid 监控和防火墙
        try {
            druidDataSource.setFilters("stat,wall");
        } catch (SQLException e) {
            throw new IOException("Failed to set Druid filters", e);
        }

        this.dataSource = druidDataSource;
    }

    @Override
    public @NotNull DatabaseType getType() {
        return DatabaseType.POSTGRES;
    }

    @Override
    protected @NotNull DataSource createWriteDataSource() {
        return dataSource;
    }

    @Override
    protected @NotNull DataSource createReadDataSource() {
        return dataSource;
    }
}
