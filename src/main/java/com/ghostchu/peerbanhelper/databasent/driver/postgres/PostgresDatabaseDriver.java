package com.ghostchu.peerbanhelper.databasent.driver.postgres;

import org.stone.beecp.BeeDataSource;
import com.ghostchu.peerbanhelper.databasent.DatabaseType;
import com.ghostchu.peerbanhelper.databasent.driver.AbstractDatabaseDriver;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.stone.beecp.BeeDataSourceConfig;

import javax.sql.DataSource;
import java.io.IOException;

public class PostgresDatabaseDriver extends AbstractDatabaseDriver {
    private final ConfigurationSection section;
    private final BeeDataSource dataSource;

    public PostgresDatabaseDriver(@NotNull ConfigurationSection section) throws IOException {
        super();
        this.section = section;
        BeeDataSourceConfig config = new BeeDataSourceConfig();
        
        String host = section.getString("host");
        int port = section.getInt("port");
        String database = section.getString("database");
        String username = section.getString("username");
        String password = section.getString("password");

        config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaxActive(10);
        config.setMaxWait(30000);
        config.setIntervalOfClearTimeout(600000L);

        // 连接池验证配置
        config.setAliveTestSql("SELECT 1");

        // 启用公平排队 (FIFO)
        config.setFairMode(true);
        
        // PostgreSQL 事务中 schema/catalog 变更支持
        config.setForceDirtyWhenSetSchema(true);

        // PostgreSQL-specific optimizations
        config.addConnectionFactoryProperty("ApplicationName", "PeerBanHelper");
        config.addConnectionFactoryProperty("tcpKeepAlive", "true");
        config.addConnectionFactoryProperty("reWriteBatchedInserts", "true"); // Improve batch insert performance

        this.dataSource = new BeeDataSource(config);
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
