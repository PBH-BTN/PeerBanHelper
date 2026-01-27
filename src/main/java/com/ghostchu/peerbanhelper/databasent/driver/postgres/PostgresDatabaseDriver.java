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

    public PostgresDatabaseDriver(@NotNull ConfigurationSection section) throws IOException {
        super();
        this.section = section;
    }

    @Override
    public @NotNull DatabaseType getType() {
        return DatabaseType.POSTGRES;
    }

    @Override
    public @NotNull DataSource createDataSource() {
        // Hikari CP PostgreSQL DataSource implementation
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
        config.setMaximumPoolSize(section.getInt("pool.max-size"));
        config.setMinimumIdle(section.getInt("pool.min-idle"));
        config.setIdleTimeout(section.getLong("pool.idle-timeout-millis"));
        config.setConnectionTimeout(30000);

        // PostgreSQL-specific optimizations
        config.addDataSourceProperty("ApplicationName", "PeerBanHelper");
        config.addDataSourceProperty("tcpKeepAlive", "true");
        config.addDataSourceProperty("reWriteBatchedInserts", "true"); // Improve batch insert performance

        config.setThreadFactory(Thread.ofVirtual().name("HikariCP-PostgresPool").factory());

        return new HikariDataSource(config);
    }
}
