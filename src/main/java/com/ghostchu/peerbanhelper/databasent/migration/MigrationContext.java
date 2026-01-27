package com.ghostchu.peerbanhelper.databasent.migration;

import com.ghostchu.peerbanhelper.util.ipdb.IPDBManager;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared context for migration operations
 * Provides access to services and caches to avoid repeated lookups
 */
@Getter
@Setter
public class MigrationContext {
    /**
     * Batch size for reading from SQLite and inserting to target DB
     */
    private int batchSize = 2000;

    /**
     * IPDBManager for GeoIP lookups
     */
    private IPDBManager ipdbManager;

    /**
     * Torrent ID mapping: SQLite ID -> New DB ID
     */
    private Map<Long, Long> torrentIdMap = new HashMap<>();

    /**
     * Module name to ID mapping
     */
    private Map<String, Long> moduleIdMap = new HashMap<>();

    /**
     * Whether to skip GeoIP generation for performance
     */
    private boolean skipGeoIP = false;

    /**
     * Statistics: total records migrated across all tables
     */
    private long totalRecordsMigrated = 0;

    public void incrementTotalRecords(long count) {
        totalRecordsMigrated += count;
    }

    /**
     * Calculate and format migration progress percentage
     *
     * @param current Current count
     * @param total   Total count
     * @return Formatted percentage string
     */
    public static String formatProgress(long current, long total) {
        if (total == 0) {
            return "0%";
        }
        return String.format("%.1f%%", (current * 100.0 / total));
    }

    /**
     * Check if should log progress (every 10% or on specific milestones)
     *
     * @param current    Current count
     * @param total      Total count
     * @param lastLogged Last logged count
     * @return true if should log progress
     */
    public static boolean shouldLogProgress(long current, long total, long lastLogged) {
        if (total == 0) return false;
        if (current == total) return true; // Always log completion

        // Log every 10% or every 1000 records, whichever is more frequent
        int logInterval = Math.min((int) (total / 10), 1000);
        if (logInterval == 0) logInterval = 100;

        return (current - lastLogged) >= logInterval;
    }
}
