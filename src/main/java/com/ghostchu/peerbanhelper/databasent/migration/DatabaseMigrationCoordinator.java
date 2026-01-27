package com.ghostchu.peerbanhelper.databasent.migration;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.databasent.migration.migrators.*;
import com.ghostchu.peerbanhelper.databasent.service.*;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.ipdb.IPDBManager;
import io.sentry.Sentry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

/**
 * Coordinates the migration from SQLite/ORMLite to MyBatis-Plus
 * Triggers after Flyway and MyBatis-Plus initialization
 */
@Slf4j
@Component
public class DatabaseMigrationCoordinator {
    @Autowired
    private AlertService alertService;
    @Autowired
    private BanListService banListService;
    @Autowired
    private MetadataService metadataService;
    @Autowired
    private TorrentService torrentService;
    @Autowired
    private HistoryService historyService;
    @Autowired
    private PeerRecordService peerRecordService;
    @Autowired
    private TrafficJournalService trafficJournalService;
    @Autowired
    private RuleSubInfoService ruleSubInfoService;
    @Autowired
    private RuleSubLogService ruleSubLogService;
    @Autowired
    private PCBAddressService pcbAddressService;
    @Autowired
    private PCBRangeService pcbRangeService;
    @Autowired
    private PeerConnectionMetricsService peerConnectionMetricsService;
    @Autowired
    private PeerConnectionMetricsTrackService peerConnectionMetricsTrackService;
    @Autowired(required = false)
    private IPDBManager ipdbManager;


    private final File sqliteDbFile;
    private final File migrationMarkerFile;
    @Autowired
    private PlatformTransactionManager platformTransactionManager;

    public DatabaseMigrationCoordinator() {
        File persistDir = new File(Main.getDataDirectory(), "persist");
        this.sqliteDbFile = new File(persistDir, "peerbanhelper.db");
        this.migrationMarkerFile = new File(persistDir, "migration_completed.marker");
    }

    @PostConstruct
    public void checkAndMigrate() {
        // Check if migration is needed
        if (!shouldMigrate()) {
            log.debug("Database migration not needed or already completed");
            return;
        }

        log.info("=".repeat(60));
        log.info(tlUI(Lang.DBNT_MIGRATOR_STARTING));
        log.info("=".repeat(60));

        try {
            performMigration();
            markMigrationCompleted();
            log.info("=".repeat(60));
            log.info(tlUI(Lang.DBNT_MIGRATOR_COMPLETED));
            log.info("=".repeat(60));
        } catch (Exception e) {
            log.error(tlUI(Lang.DBNT_MIGRATOR_FAILED), e);
            Sentry.captureException(e);
            // Don't throw - allow application to start even if migration fails
            // User can manually fix and restart
        }
    }

    private boolean shouldMigrate() {
        // Skip if marker file exists
        if (migrationMarkerFile.exists()) {
            log.debug("Migration marker file exists, skipping migration");
            return false;
        }

        // Check if SQLite database exists
        if (!sqliteDbFile.exists()) {
            log.debug("SQLite database file not found at {}, skipping migration", sqliteDbFile);
            return false;
        }

        log.debug("Found SQLite database at {}, migration will proceed", sqliteDbFile);
        return true;
    }

    private void performMigration() throws Exception {
        long startTime = System.currentTimeMillis();

        // Connect to SQLite database
        String jdbcUrl = "jdbc:sqlite:" + sqliteDbFile.getAbsolutePath();
        log.info(tlUI(Lang.DBNT_MIGRATOR_CONNECTING_SQLITE));
        TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
        try (Connection sqliteConnection = DriverManager.getConnection(jdbcUrl)) {
            // Upgrade SQLite schema to version 20
            SQLiteSchemaUpgrader upgrader = new SQLiteSchemaUpgrader(sqliteConnection);
            upgrader.upgradeToLatest();

            // Create migration context
            MigrationContext context = new MigrationContext();
            context.setIpdbManager(ipdbManager);
            context.setBatchSize(500);
            context.setSkipGeoIP(false); // Set to true for faster migration if needed

            // Create migrators in dependency order
            List<TableMigrator> migrators = createMigrators();

            // Sort by migration order
            migrators.sort(Comparator.comparingInt(TableMigrator::getMigrationOrder));

            // Execute migrations
            for (TableMigrator migrator : migrators) {
                try {
                    if (!migrator.isTableAvailable(sqliteConnection)) {
                        log.info("Table '{}' not found in SQLite, skipping", migrator.getTableName());
                        continue;
                    }

                    log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_PREPARE, migrator.getTableName()));
                    transactionTemplate.execute(_ -> {
                        try {
                            return migrator.migrate(sqliteConnection, context);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (Exception e) {
                    log.error("Failed to migrate table '{}': {}",
                            migrator.getTableName(), e.getMessage(), e);
                    // Continue with other tables
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_FINISHED, context.getTotalRecordsMigrated(), duration / 1000));
        }
    }

    private List<TableMigrator> createMigrators() {
        List<TableMigrator> migrators = new ArrayList<>();

        // Add all migrators
        migrators.add(new MetadataMigrator(metadataService));
        migrators.add(new TorrentMigrator(torrentService));
        migrators.add(new HistoryMigrator(historyService));
        migrators.add(new PeerRecordMigrator(peerRecordService));
        migrators.add(new TrafficJournalMigrator(trafficJournalService));
        migrators.add(new RuleSubInfoMigrator(ruleSubInfoService));
        migrators.add(new RuleSubLogMigrator(ruleSubLogService));
        migrators.add(new PCBAddressMigrator(pcbAddressService));
        migrators.add(new PCBRangeMigrator(pcbRangeService));
        migrators.add(new PeerConnectionMetricsMigrator(peerConnectionMetricsService));
        migrators.add(new PeerConnectionMetricsTrackMigrator(peerConnectionMetricsTrackService));
        migrators.add(new AlertMigrator(alertService));
        migrators.add(new BanListMigrator(banListService));

        return migrators;
    }

    private void markMigrationCompleted() {
        try {
            migrationMarkerFile.createNewFile();
            log.debug("Created migration marker file: {}", migrationMarkerFile);
        } catch (Exception e) {
            log.warn("Failed to create migration marker file", e);
        }
    }
}
