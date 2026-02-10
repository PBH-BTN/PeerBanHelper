package com.ghostchu.peerbanhelper.databasent.driver.mysql;

import org.stone.beecp.BeeDataSource;
import com.ghostchu.peerbanhelper.databasent.DatabaseType;
import com.ghostchu.peerbanhelper.databasent.driver.AbstractDatabaseDriver;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.io.IOException;

public class MySQLDatabaseDriver extends AbstractDatabaseDriver {
    private final ConfigurationSection section;
    private final BeeDataSource dataSource;

    public MySQLDatabaseDriver(@NotNull ConfigurationSection section) throws IOException {
        super();
        this.section = section;
        
        String host = section.getString("host");
        int port = section.getInt("port");
        String database = section.getString("database");
        String username = section.getString("username");
        String password = section.getString("password");
        
        BeeDataSource beeDataSource = new BeeDataSource();
        beeDataSource.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
        beeDataSource.setUsername(username);
        beeDataSource.setPassword(password);
        beeDataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        beeDataSource.setMaxActive(10);
        beeDataSource.setMaxWait(30000);
        beeDataSource.setIntervalOfClearTimeout(600000L);
        
        // 连接池验证配置
        beeDataSource.setAliveTestSql("SELECT 1");
        
        // 启用公平排队 (FIFO)
        beeDataSource.setFairMode(true);
        
        // MySQL 优化参数
        beeDataSource.addConnectionFactoryProperty("cachePrepStmts", "true");
        beeDataSource.addConnectionFactoryProperty("prepStmtCacheSize", "250");
        beeDataSource.addConnectionFactoryProperty("prepStmtCacheSqlLimit", "2048");
        
        this.dataSource = beeDataSource;
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
