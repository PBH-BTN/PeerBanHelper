package com.ghostchu.peerbanhelper.databasent.migration;

import com.ghostchu.peerbanhelper.text.Lang;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

/**
 * Upgrades old SQLite database schema to version 20 (target version)
 * based on upgrade scripts from DatabaseHelper.java
 */
@Slf4j
public class SQLiteSchemaUpgrader {
    private final Connection connection;

    public SQLiteSchemaUpgrader(Connection connection) {
        this.connection = connection;
    }

    /**
     * Get current schema version from metadata table
     */
    public int getCurrentVersion() throws SQLException {
        // Create metadata table if not exists
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS metadata (\"key\" VARCHAR PRIMARY KEY, \"value\" VARCHAR)");
        }

        String versionQuery = "SELECT value FROM metadata WHERE \"key\" = 'version'";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(versionQuery)) {
            if (rs.next()) {
                return Integer.parseInt(rs.getString(1));
            } else {
                // No version found, assume version 20 (latest) or 2 (very old)
                // Check if we have old tables to determine
                return detectInitialVersion();
            }
        }
    }

    private int detectInitialVersion() {
        // Check if downloader column exists in history table
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(history)")) {
            boolean hasDownloader = false;
            while (rs.next()) {
                String columnName = rs.getString("name");
                if ("downloader".equals(columnName)) {
                    hasDownloader = true;
                    break;
                }
            }
            return hasDownloader ? 3 : 2;
        } catch (SQLException e) {
            // If table doesn't exist, assume version 20 (no migration needed)
            return 20;
        }
    }

    /**
     * Upgrade schema from current version to version 20
     */
    public void upgradeToLatest() throws SQLException {
        int currentVersion = getCurrentVersion();
        if (currentVersion >= 20) {
            return;
        }

        log.info(tlUI(Lang.DBNT_MIGRATOR_LEGACY_UPGRADE_PROCESS, currentVersion));
        // Perform upgrades step by step
        int v = currentVersion;

        if (v < 3) {
            upgradeToV3();
            v = 3;
        }

        // v3-v5 were dropped/skipped in original code
        if (v < 6) {
            v = 6;
        }

        if (v == 6) {
            upgradeToV7();
            v = 7;
        }

        if (v == 7) {
            upgradeToV8();
            v = 8;
        }

        if (v == 8) {
            upgradeToV9();
            v = 9;
        }

        if (v == 9) {
            upgradeToV10();
            v = 10;
        }

        if (v == 10) {
            upgradeToV11();
            v = 11;
        }

        if (v <= 14) {
            upgradeToV15();
            v = 15;
        }

        if (v == 15) {
            upgradeToV16();
            v = 16;
        }

        if (v == 16) {
            // dropped
            v = 17;
        }

        if (v <= 18) {
            upgradeToV19();
            v = 19;
        }

        if (v <= 19) {
            upgradeToV20();
            v = 20;
        }

        // Update version in metadata
        updateVersion(v);
        log.info("SQLite schema upgrade completed to version {}", v);
    }

    private void upgradeToV3() {
        log.info("Upgrading to v3: Adding downloader column to history table");
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE history ADD COLUMN downloader VARCHAR DEFAULT ''");
        } catch (SQLException e) {
            log.warn("Failed to add downloader column (may already exist): {}", e.getMessage());
        }
    }

    private void upgradeToV7() {
        log.info("Upgrading to v7: Recreating alert table");
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DROP TABLE IF EXISTS alert");
            stmt.executeUpdate("CREATE TABLE alert (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "createAt TIMESTAMP NOT NULL, " +
                    "readAt TIMESTAMP, " +
                    "level VARCHAR NOT NULL, " +
                    "identifier VARCHAR NOT NULL, " +
                    "title VARCHAR NOT NULL, " +
                    "content VARCHAR NOT NULL)");
        } catch (SQLException e) {
            log.error("Failed to recreate alert table: {}", e.getMessage());
            Sentry.captureException(e);
        }
    }

    private void upgradeToV8() {
        log.info("Upgrading to v8: Recreating alert table again");
        upgradeToV7(); // Same as v7
    }

    private void upgradeToV9() {
        log.info("Upgrading to v9: Adding privateTorrent column to torrents table");
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE torrents ADD COLUMN privateTorrent BOOLEAN NULL");
        } catch (SQLException e) {
            log.warn("Failed to add privateTorrent column (may already exist): {}", e.getMessage());
        }
    }

    private void upgradeToV10() {
        log.info("Upgrading to v10: Adding downloaderProgress column to history table");
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE history ADD COLUMN downloaderProgress DOUBLE NULL");
        } catch (SQLException e) {
            log.warn("Failed to add downloaderProgress column (may already exist): {}", e.getMessage());
        }
    }

    private void upgradeToV11() {
        log.info("Upgrading to v11: Adding speed columns to peer_records table");
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE peer_records ADD COLUMN uploadSpeed BIGINT NOT NULL DEFAULT 0");
            stmt.executeUpdate("ALTER TABLE peer_records ADD COLUMN downloadSpeed BIGINT NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            log.warn("Failed to add speed columns (may already exist): {}", e.getMessage());
        }
    }

    private void upgradeToV15() {
        log.info("Upgrading to v15: Adding structuredData column to history table");
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE history ADD COLUMN structuredData TEXT NOT NULL DEFAULT '{}'");
        } catch (SQLException e) {
            log.warn("Failed to add structuredData column (may already exist): {}", e.getMessage());
        }
    }

    private void upgradeToV16() {
        log.info("Upgrading to v16: DownloaderName conversion (handled during migration)");
        // This upgrade involves data transformation, will be handled during migration
        // Original code converted downloader names using ConfigTransfer.downloaderNameToUUID
    }

    private void upgradeToV19() {
        log.info("Upgrading to v19: Clearing banlist for IPAddress format change");
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DELETE FROM banlist");
        } catch (SQLException e) {
            log.warn("Failed to clear banlist: {}", e.getMessage());
        }
    }

    private void upgradeToV20() {
        log.info("Upgrading to v20: Adding port column to peer_records table");
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE peer_records ADD COLUMN port INT NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            log.warn("Failed to add port column (may already exist): {}", e.getMessage());
        }
    }

    private void updateVersion(int version) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(String.format(
                    "INSERT OR REPLACE INTO metadata (\"key\", \"value\") VALUES ('version', '%d')",
                    version
            ));
        }
    }

    /**
     * Check if a table exists in the database
     */
    public boolean tableExists(String tableName) throws SQLException {
        String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='" + tableName + "'";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            return rs.next();
        }
    }
}
