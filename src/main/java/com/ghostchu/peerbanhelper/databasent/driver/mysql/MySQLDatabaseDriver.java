package com.ghostchu.peerbanhelper.databasent.driver.mysql;

import com.ghostchu.peerbanhelper.databasent.DatabaseType;
import com.ghostchu.peerbanhelper.databasent.driver.AbstractDatabaseDriver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.io.IOException;

public class MySQLDatabaseDriver extends AbstractDatabaseDriver {
    private final ConfigurationSection section;

    public MySQLDatabaseDriver(@NotNull ConfigurationSection section) throws IOException {
        super();
        this.section = section;
    }

    @Override
    public @NotNull DatabaseType getType() {
        return DatabaseType.MYSQL;
    }

    @Override
    public @NotNull DataSource getDataSource() {
        // Hikari CP SQLite DataSource implementation
        HikariConfig config = new HikariConfig();
        String host = section.getString("host");
        int port = section.getInt("port");
        String database = section.getString("database");
        String username = section.getString("username");
        String password = section.getString("password");
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC");
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(section.getInt("pool.max-size"));
        config.setMinimumIdle(section.getInt("pool.min-idle"));
        config.setIdleTimeout(section.getLong("pool.idle-timeout-millis"));
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.setThreadFactory(Thread.ofVirtual().name("HikariCP-MySQLPool").factory());
        return new HikariDataSource(config);
    }
}
