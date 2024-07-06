package com.ghostchu.peerbanhelper.database;

import com.ghostchu.peerbanhelper.Main;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

@Component
public class DatabaseManager {
    private HikariDataSource ds;

    public DatabaseManager() {
        File databaseDirectory = new File(Main.getDataDirectory(), "persist");
        if (!databaseDirectory.exists()) {
            databaseDirectory.mkdirs();
        }
        File sqliteDb = new File(databaseDirectory, "persist-data.db");
        setupDatabase(sqliteDb);
    }

    public void setupDatabase(File file) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("PeerBanHelper SQLite Connection Pool");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + file);
        config.setConnectionTestQuery("SELECT 1");
        config.setMaxLifetime(60000); // 60 Sec
        config.setMaximumPoolSize(4); // 50 Connections (including idle connections)
        this.ds = new HikariDataSource(config);
    }

    public void close() {
        this.ds.close();
    }

    public Connection getConnection() throws SQLException {
        return ds.getConnection();
    }
}
