package com.ghostchu.peerbanhelper.databasent.driver.postgres;

import com.ghostchu.peerbanhelper.databasent.DatabaseType;
import com.ghostchu.peerbanhelper.databasent.driver.AbstractDatabaseDriver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.io.IOException;

public class PostgresDatabaseDriver extends AbstractDatabaseDriver {
    private final ConfigurationSection section;
    private final HikariDataSource dataSource;

    public PostgresDatabaseDriver(@NotNull ConfigurationSection section) throws IOException {
        super();
        this.section = section;
        HikariConfig config = new HikariConfig();
        String host = section.getString("host");
        int port = section.getInt("port");
        String database = section.getString("database");
        String username = section.getString("username");
        String password = section.getString("password");

        config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(1);
        config.setIdleTimeout(600000);

        // PostgreSQL-specific optimizations
        config.addDataSourceProperty("ApplicationName", "PeerBanHelper");
        config.addDataSourceProperty("tcpKeepAlive", "true");
        config.addDataSourceProperty("reWriteBatchedInserts", "true"); // Improve batch insert performance

        config.setThreadFactory(Thread.ofVirtual().name("HikariCP-PostgresPool").factory());

        this.dataSource = new HikariDataSource(config);
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
