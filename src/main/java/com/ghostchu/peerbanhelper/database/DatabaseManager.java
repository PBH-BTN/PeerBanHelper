package com.ghostchu.peerbanhelper.database;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class DatabaseManager {
    private File databaseDirectory;
    private HikariDataSource ds;
    private ScheduledExecutorService cleanupService = Executors.newScheduledThreadPool(1);

    public DatabaseManager(PeerBanHelperServer server) {
        databaseDirectory = new File(Main.getDataDirectory(), "persist");
        if(!databaseDirectory.exists()){
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
        config.setIdleTimeout(45000); // 45 Sec
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
