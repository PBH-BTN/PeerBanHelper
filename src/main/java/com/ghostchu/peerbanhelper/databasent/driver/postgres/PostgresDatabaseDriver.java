package com.ghostchu.peerbanhelper.databasent.driver.postgres;

import org.stone.beecp.BeeDataSource;
import com.ghostchu.peerbanhelper.databasent.DatabaseType;
import com.ghostchu.peerbanhelper.databasent.driver.AbstractDatabaseDriver;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.io.IOException;

public class PostgresDatabaseDriver extends AbstractDatabaseDriver {
    private final ConfigurationSection section;
    private final BeeDataSource dataSource;

    public PostgresDatabaseDriver(@NotNull ConfigurationSection section) throws IOException {
        super();
        this.section = section;
        
        String host = section.getString("host");
        int port = section.getInt("port");
        String database = section.getString("database");
        String username = section.getString("username");
        String password = section.getString("password");

        BeeDataSource beeDataSource = new BeeDataSource();
        beeDataSource.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
        beeDataSource.setUsername(username);
        beeDataSource.setPassword(password);
        beeDataSource.setDriverClassName("org.postgresql.Driver");
        beeDataSource.setMaxActive(10);
        beeDataSource.setMaxWait(30000);
        beeDataSource.setIntervalOfClearTimeout(600000L);

        // 连接池验证配置
        beeDataSource.setAliveTestSql("SELECT 1");

        // 启用公平排队 (FIFO)
        beeDataSource.setFairMode(true);
        
        // PostgreSQL 事务中 schema/catalog 变更支持
        beeDataSource.setForceDirtyWhenSetSchema(true);

        // PostgreSQL-specific optimizations
        beeDataSource.addConnectionFactoryProperty("ApplicationName", "PeerBanHelper");
        beeDataSource.addConnectionFactoryProperty("tcpKeepAlive", "true");
        beeDataSource.addConnectionFactoryProperty("reWriteBatchedInserts", "true"); // Improve batch insert performance

        this.dataSource = beeDataSource;
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
