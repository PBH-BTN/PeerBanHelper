package com.ghostchu.peerbanhelper.databasent.driver.mysql;

import org.stone.beecp.BeeDataSource;
import com.ghostchu.peerbanhelper.databasent.DatabaseType;
import com.ghostchu.peerbanhelper.databasent.driver.AbstractDatabaseDriver;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.stone.beecp.BeeDataSourceConfig;

import javax.sql.DataSource;
import java.io.IOException;

import static com.ghostchu.peerbanhelper.util.MiscUtil.removeBeeCPShutdownHook;

public class MySQLDatabaseDriver extends AbstractDatabaseDriver {
    private final ConfigurationSection section;
    private final BeeDataSource dataSource;

    public MySQLDatabaseDriver(@NotNull ConfigurationSection section) throws IOException {
        super();
        this.section = section;
        BeeDataSourceConfig config = new BeeDataSourceConfig();
        
        String host = section.getString("host");
        int port = section.getInt("port");
        String database = section.getString("database");
        String username = section.getString("username");
        String password = section.getString("password");

        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaxActive(10);
        config.setMaxWait(30000);
        config.setIntervalOfClearTimeout(600000L);
        
        // 连接池验证配置
        config.setAliveTestSql("SELECT 1");
        
        // 启用公平排队 (FIFO)
        config.setFairMode(true);
        
        // MySQL 优化参数
        config.addConnectionFactoryProperty("cachePrepStmts", "true");
        config.addConnectionFactoryProperty("prepStmtCacheSize", "250");
        config.addConnectionFactoryProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new BeeDataSource(config);
        removeBeeCPShutdownHook(this.dataSource);
    }

    @Override
    public @NotNull DatabaseType getType() {
        return DatabaseType.MYSQL;
    }

    @Override
    protected @NotNull DataSource createReadDataSource() {
        return dataSource;
    }

    @Override
    protected @NotNull DataSource createWriteDataSource() {
        return dataSource;
    }
}
