package com.ghostchu.peerbanhelper.databasent.driver.postgres;

import com.ghostchu.peerbanhelper.databasent.DatabaseType;
import com.ghostchu.peerbanhelper.databasent.driver.AbstractDatabaseDriver;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.stone.beecp.BeeDataSource;
import org.stone.beecp.BeeDataSourceConfig;

import javax.sql.DataSource;
import java.io.IOException;

import static com.ghostchu.peerbanhelper.util.MiscUtil.removeBeeCPShutdownHook;

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

        config.setJdbcUrl("jdbc:p6spy:postgresql://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);
        //config.setDriverClassName("org.postgresql.Driver");
        config.setDriverClassName("com.p6spy.engine.spy.P6SpyDriver");
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
        removeBeeCPShutdownHook(dataSource);
    }

    @Override
    public @NotNull DatabaseType getType() {
        return DatabaseType.POSTGRES;
    }

    @Override
    protected @NotNull DataSource createDataSource() {
        return dataSource;
    }
}
