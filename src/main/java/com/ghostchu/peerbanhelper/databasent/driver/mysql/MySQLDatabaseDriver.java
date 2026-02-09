package com.ghostchu.peerbanhelper.databasent.driver.mysql;

import com.alibaba.druid.pool.DruidDataSource;
import com.ghostchu.peerbanhelper.databasent.DatabaseType;
import com.ghostchu.peerbanhelper.databasent.driver.AbstractDatabaseDriver;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.SQLException;

public class MySQLDatabaseDriver extends AbstractDatabaseDriver {
    private final ConfigurationSection section;
    private final DruidDataSource dataSource;

    public MySQLDatabaseDriver(@NotNull ConfigurationSection section) throws IOException {
        super();
        this.section = section;
        
        String host = section.getString("host");
        int port = section.getInt("port");
        String database = section.getString("database");
        String username = section.getString("username");
        String password = section.getString("password");
        
        DruidDataSource druidDataSource = new DruidDataSource();
        druidDataSource.setUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
        druidDataSource.setUsername(username);
        druidDataSource.setPassword(password);
        druidDataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
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
        
        // MySQL 优化参数
        druidDataSource.addConnectionProperty("cachePrepStmts", "true");
        druidDataSource.addConnectionProperty("prepStmtCacheSize", "250");
        druidDataSource.addConnectionProperty("prepStmtCacheSqlLimit", "2048");
        
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
