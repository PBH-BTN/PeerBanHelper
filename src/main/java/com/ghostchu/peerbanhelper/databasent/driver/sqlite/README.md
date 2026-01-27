# SQLite Database Support for PeerBanHelper

This document describes the SQLite database implementation added to the PeerBanHelper project.

## Overview

SQLite support has been added as an alternative database backend alongside H2 and MySQL. SQLite is a lightweight, file-based database that requires no separate server process.

## Configuration

### Enable SQLite Database

In `config.yml`, set the database type to `2`:

```yaml
database:
  # 0=H2, 1=MySQL, 2=SQLite
  type: 2
```

### Database File Location

The SQLite database file is automatically created at:
```
data/persist/peerbanhelper-nt.db
```

### Connection Pool Settings

SQLite uses a single-connection pool (max-size=1) due to its write serialization limitations:

```yaml
database:
  pool:
    max-size: 1          # Forced to 1 for SQLite
    min-idle: 1          # Forced to 1 for SQLite
    idle-timeout-millis: 600000
```

## Features

### Performance Optimizations

The SQLite driver applies the following PRAGMA settings automatically:

- **WAL Mode**: `PRAGMA journal_mode = WAL` - Write-Ahead Logging for better concurrency
- **Synchronous Mode**: `PRAGMA synchronous = NORMAL` - Balanced performance and safety
- **Memory Mapping**: `PRAGMA mmap_size = 0` - Disabled for stability
- **Soft Heap Limit**: Configurable via `pbh.database.sqliteSoftHeapLimitBytes` (default: 33MB)
- **Auto-Optimize**: `PRAGMA OPTIMIZE` runs on startup

### Maintenance

#### Automatic VACUUM

The SQLite driver performs automatic VACUUM operations based on the configured interval:

```yaml
persist:
  vacuum-interval-days: 7  # Default: 7 days
```

VACUUM reclaims unused space and optimizes the database file. A maintenance timestamp is tracked in:
```
data/persist/peerbanhelper-nt.db.maintenance
```

#### Disable VACUUM

To disable automatic VACUUM (not recommended):
```bash
-Dpbh.database.disableSQLiteVacuum=true
```

#### Disable PRAGMA Settings

To disable all PRAGMA optimizations (not recommended):
```bash
-Dpbh.database.disableSQLitePragmaSettings=true
```

## Schema

### Migration Scripts

SQLite schema migrations are located in:
```
src/main/resources/db/migration/sqlite/
```

Current migrations:
- `V1_1__initial_sqlite.sql` - Initial schema with all tables and indexes
- `V1_2__add_index_for_history_traffics.sql` - Additional history table indexes

### Differences from MySQL Schema

The SQLite schema adapts MySQL syntax with these key differences:

1. **Auto-increment**: `INTEGER PRIMARY KEY AUTOINCREMENT` instead of `BIGINT UNSIGNED AUTO_INCREMENT`
2. **Date/Time**: `INTEGER` fields storing millisecond UNIX timestamps instead of `datetime`
3. **Boolean**: `INTEGER` (0/1) instead of `TINYINT` or `BOOLEAN`
4. **Decimal**: `REAL` instead of `DOUBLE` or `DECIMAL`
5. **Constraints**: Uses `CREATE UNIQUE INDEX` instead of `ALTER TABLE ADD CONSTRAINT`

## MyBatis Mappers

### SQL Dialect Adaptations

SQLite mapper XMLs are located in:
```
src/main/resources/mapper/sqlite/
```

Key SQL syntax adaptations:

#### String Concatenation
```sql
-- MySQL
WHERE ip LIKE CONCAT(#{filter}, '%')

-- SQLite
WHERE ip LIKE #{filter} || '%'
```

#### Substring Function
```sql
-- MySQL
SUBSTRING(field, 1, #{length})

-- SQLite
SUBSTR(field, 1, #{length})
```

#### Decimal Casting
```sql
-- MySQL
CAST(value AS DECIMAL(20, 10))

-- SQLite
CAST(value AS REAL)
```

#### MAX/GREATEST Function
```sql
-- MySQL
GREATEST(0, value)

-- SQLite
MAX(0, value)
```

#### Backticks
```sql
-- MySQL
`timestamp`, `range`

-- SQLite
timestamp, "range"
```

## Technical Details

### Driver Implementation

- **Class**: `com.ghostchu.peerbanhelper.databasent.driver.sqlite.SQLiteDatabaseDriver`
- **JDBC URL**: `jdbc:sqlite:<absolute-path-to-db-file>`
- **JDBC Driver**: `org.sqlite.JDBC`
- **Connection Pool**: HikariCP with max-size=1

### Type Mappings

MyBatis Plus handles type conversions with custom TypeHandlers:

- `OffsetDateTime` ↔ INTEGER (millisecond UNIX timestamp) - **Custom OffsetDateTimeTypeHandlerForSQLite**
- `InetAddress` ↔ TEXT (IP address string)
- `Boolean` ↔ INTEGER (0/1)
- `Long` ↔ INTEGER
- `Double` ↔ REAL

**Important**: The SQLite JDBC driver does not support `ResultSet.getObject(columnIndex, OffsetDateTime.class)`. 
A custom TypeHandler (`OffsetDateTimeTypeHandlerForSQLite`) is required to handle this by:
- **Writing**: Converts `OffsetDateTime` to millisecond UNIX timestamp (`long`)
- **Reading**: Converts millisecond timestamp back to `OffsetDateTime` using system default timezone

This approach provides:
- **Space efficiency**: INTEGER uses 8 bytes vs TEXT which can use 20+ bytes
- **Query performance**: Numeric comparisons are faster than text parsing
- **Index efficiency**: INTEGER indexes are more compact and faster

### Pagination

SQLite pagination uses `LIMIT` and `OFFSET`:
```sql
SELECT * FROM table ORDER BY id LIMIT 10 OFFSET 20
```

MyBatis Plus `DbType.SQLITE` handles this automatically.

## Limitations

### Write Concurrency

SQLite serializes write operations. Only one write transaction can proceed at a time. This is acceptable for PeerBanHelper's use case but may become a bottleneck with:

- Very high peer update rates (>1000/sec)
- Many concurrent downloaders
- Frequent bulk operations

Consider MySQL for high-concurrency deployments.

### Data Types

SQLite has limited native types (NULL, INTEGER, REAL, TEXT, BLOB). All other types are stored as these primitives:

- **Date/time**: Stored as INTEGER (millisecond UNIX timestamps)
  - More efficient than TEXT storage
  - Faster range queries and comparisons
  - Smaller storage footprint and index size
- **Decimal calculations**: Stored as REAL with floating-point limitations

### Maximum Database Size

SQLite supports databases up to 281 TB, but performance may degrade with very large files (>10GB). Regular VACUUM helps maintain performance.

## Troubleshooting

### Database Locked Errors

If you see "database is locked" errors:

1. Ensure only one PeerBanHelper instance is running
2. Check that no other process has the DB file open
3. Increase `busy_timeout` (default: 30000ms)
4. Consider switching to MySQL for better concurrency

### Migration Failures

If Flyway migration fails:

```bash
# Skip validation (not recommended)
-Ddbnt.flyway.validateOnMigrate=false
```

Better approach: backup data, delete DB file, and restart.

### Performance Issues

1. Enable WAL mode (enabled by default)
2. Reduce VACUUM frequency if I/O is bottleneck
3. Monitor database file size
4. Consider MySQL for large datasets (>1M ban records)

## Migration from Old SQLite Database

If you have data in the old SQLite database (`data/persist/peerbanhelper.db`), use the built-in migration tool to transfer data to the new schema.

## Comparison with Other Backends

| Feature | SQLite | H2 | MySQL |
|---------|--------|-----|--------|
| Setup Complexity | ★☆☆ | ★☆☆ | ★★★ |
| Write Concurrency | Low | Medium | High |
| Max DB Size | 281 TB | 2 TB | Unlimited |
| Server Required | No | No | Yes |
| Clustering | No | No | Yes |
| Best For | Single-user, embedded | Development, testing | Production, multi-user |

## Recommendations

**Use SQLite if:**
- Single PeerBanHelper instance
- Moderate traffic (<500 peers/sec)
- Simple deployment requirements
- No external database server available

**Use MySQL if:**
- High concurrency requirements
- Multiple PeerBanHelper instances (future)
- Database replication needed
- Very large datasets (>1M records)

**Use H2 if:**
- Development/testing
- Need MySQL-like features without server
- Temporary deployments
