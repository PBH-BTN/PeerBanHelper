# Database Migration from SQLite/ORMLite to MyBatis-Plus

## Overview

This migration system automatically migrates data from the old SQLite/ORMLite database to the new MyBatis-Plus system (
supporting H2/MySQL/PostgreSQL).

## Features

- **Automatic Schema Upgrade**: Upgrades old SQLite schemas (v2-v19) to version 20 before migration
- **Batch Processing**: Processes records in configurable batches (default: 500) to minimize memory usage
- **Progress Tracking**: Logs migration progress for large datasets
- **Field Transformation**: Handles field type changes (Timestamp → OffsetDateTime, VARCHAR → InetAddress, etc.)
- **GeoIP Generation**: Automatically generates GeoIP data for IP addresses in history and peer_records tables
- **Dependency Ordering**: Migrates tables in correct order to handle foreign key relationships
- **Error Resilience**: Continues migration even if individual records fail
- **Conflict Resolution**: SQLite data overwrites existing data in case of conflicts

## Migrated Tables

1. **metadata** - Application metadata (order: 1)
2. **torrents** - Torrent information (order: 10)
3. **history** - Ban history records with GeoIP data (order: 50)
4. **peer_records** - Peer tracking data with GeoIP (order: 60)
5. **traffic_journal_v3** - Traffic statistics (order: 70)
6. **rule_sub_info** - Rule subscription information (order: 80)
7. **rule_sub_log** - Rule subscription logs (order: 81)
8. **pcb_address** - Progressive ban checker address tracking (order: 90)
9. **pcb_range** - Progressive ban checker range tracking (order: 91)
10. **peer_connection_metrics** - Connection metrics (order: 100)
11. **peer_connection_metrics_track** - Connection tracking details (order: 101)
12. **alert** - System alerts (order: 110)
13. **banlist** - Current ban list (order: 120, note: cleared in v19)

## Migration Trigger

Migration automatically triggers when:

1. The application starts (via `@PostConstruct` in `DatabaseMigrationCoordinator`)
2. SQLite database file exists at `data/persist/peerbanhelper.db`
3. Migration marker file does not exist

## Configuration

### Batch Size

Default batch size is 500 records. To adjust:

```java
context.setBatchSize(1000); // in DatabaseMigrationCoordinator.performMigration()
```

### Skip GeoIP Generation

For faster migration (at the cost of missing GeoIP data):

```java
context.setSkipGeoIP(true); // in DatabaseMigrationCoordinator.performMigration()
```

## Migration Process

1. **Schema Upgrade**: `SQLiteSchemaUpgrader` upgrades the SQLite database to version 20
2. **Table Detection**: Checks which tables exist in the source database
3. **Ordered Migration**: Executes migrators in dependency order
4. **Batch Processing**: Reads and inserts data in configurable batches
5. **Progress Logging**: Reports progress every batch and at milestones
6. **Completion Marker**: Creates `migration_completed.marker` file when done

## Field Mappings

### Common Transformations

- **Timestamp → OffsetDateTime**: All timestamp fields converted to system timezone
- **VARCHAR (IP) → InetAddress**: IP addresses parsed to InetAddress objects
- **BIGINT (epoch millis) → OffsetDateTime**: For traffic journal timestamps
- **JSON String → TranslationComponent**: For rule names, descriptions, alert titles/content
- **JSON String → Map<String, Object>**: For structured data fields

### Special Cases

#### History Table

- `rule_id` (SQLite) → `rule_name` (MyBatis-Plus): Requires JOIN with rules and modules tables
- `module_id` (via rules) → `module_name`: Module name extracted from JOIN
- Generates `peer_geoip` from IP address using IPDBManager

#### Peer Records Table

- Generates `peer_geoip` from IP address
- Converts speed fields and timestamps

#### Metadata Table

- Field names changed: `key` → `k`, `value` → `v`
- Skips `version` entry as it's handled separately

## Error Handling

- **Table Not Found**: Skips migration for that table, logs warning
- **Record Failure**: Logs error and continues with next record
- **Service Unavailable**: Logs error but allows application to start
- **Schema Upgrade Failure**: Attempts to continue with existing schema

## Performance Considerations

1. **Memory Management**:
    - Batch size limits memory usage
    - Calls `System.gc()` every 5000 records for large tables
    - Uses streaming queries where possible

2. **GeoIP Lookups**:
    - Can be disabled for faster migration
    - Cached in IPDBManager to reduce duplicate lookups

3. **Database Writes**:
    - Uses MyBatis-Plus `saveBatch()` for efficient batch inserts
    - Configurable batch size based on available memory

## Migration Marker

After successful migration, a marker file is created:

```
data/persist/migration_completed.marker
```

To re-run migration:

1. Stop the application
2. Delete the marker file
3. Optionally restore a SQLite backup
4. Restart the application

## Troubleshooting

### Migration Doesn't Start

- Check if `data/persist/peerbanhelper.db` exists
- Check if `migration_completed.marker` already exists
- Review startup logs for errors

### Partial Migration

- Check logs for which tables completed successfully
- Migration can be manually resumed by implementing table-specific logic
- Each migrator tracks its own progress in logs

### Out of Memory Errors

- Reduce batch size in `MigrationContext`
- Disable GeoIP generation
- Increase JVM heap size: `-Xmx4G`

### GeoIP Missing

- Ensure IPDBManager is initialized
- Check internet connectivity for GeoIP database downloads
- Review logs for IPDB-related errors

## Architecture

```
DatabaseMigrationCoordinator (Spring @Component, @PostConstruct)
  ├─ SQLiteSchemaUpgrader (upgrades schema to v20)
  ├─ MigrationContext (shared state and services)
  └─ TableMigrator implementations (one per table)
      ├─ MetadataMigrator
      ├─ TorrentMigrator
      ├─ HistoryMigrator (with GeoIP)
      ├─ PeerRecordMigrator (with GeoIP)
      ├─ TrafficJournalMigrator
      ├─ RuleSubInfoMigrator
      ├─ RuleSubLogMigrator
      ├─ PCBAddressMigrator
      ├─ PCBRangeMigrator
      ├─ PeerConnectionMetricsMigrator
      ├─ PeerConnectionMetricsTrackMigrator
      ├─ AlertMigrator
      └─ BanListMigrator
```

## Language Keys

Added to `Lang.java`:

- `DATABASE_MIGRATION_STARTING` - Migration start message
- `DATABASE_MIGRATION_COMPLETED` - Migration completion message
- `DATABASE_MIGRATION_FAILED` - Migration failure message
- `DATABASE_MIGRATION_SQLITE_VERSION` - Current SQLite version message

## Future Improvements

1. **Resume Support**: Track per-table migration status to allow resuming
2. **Parallel Migration**: Use virtual threads for independent tables
3. **Validation**: Add post-migration validation to verify data integrity
4. **Rollback**: Implement rollback mechanism for failed migrations
5. **Progress UI**: Add GUI progress indicator for desktop users
