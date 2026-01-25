package com.ghostchu.peerbanhelper.databasent.migration;

import java.sql.Connection;

/**
 * Base interface for all table migrators
 */
public interface TableMigrator {
    /**
     * Get the name of the table this migrator handles
     */
    String getTableName();

    /**
     * Perform the migration
     *
     * @param sqliteConnection Connection to the SQLite database
     * @param context          Migration context with shared resources
     * @return Number of records migrated
     */
    long migrate(Connection sqliteConnection, MigrationContext context) throws Exception;

    /**
     * Check if this table exists in the source database
     */
    boolean isTableAvailable(Connection sqliteConnection) throws Exception;

    /**
     * Get the order in which this migrator should run (lower = earlier)
     * Used to handle dependencies between tables
     */
    default int getMigrationOrder() {
        return 100; // Default order
    }
}
