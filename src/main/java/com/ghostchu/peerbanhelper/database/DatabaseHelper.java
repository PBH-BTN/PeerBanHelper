package com.ghostchu.peerbanhelper.database;

import com.ghostchu.peerbanhelper.text.Lang;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Slf4j
public class DatabaseHelper {
    private final DatabaseManager manager;

    public DatabaseHelper(DatabaseManager manager) throws SQLException {
        this.manager = manager;
        try {
            createTables();
        } catch (SQLException e) {
            log.warn(Lang.DATABASE_SETUP_FAILED, e);
            throw e;
        }
    }

    public int setMetadata(String key, String value) throws SQLException {
        try (Connection connection = manager.getConnection()) {
            @Cleanup
            PreparedStatement ps = connection.prepareStatement("REPLACE INTO metadata (key, value) VALUES (?,?)");
            ps.setString(1, key);
            ps.setString(2, value);
            return ps.executeUpdate();
        }
    }

    public String getMetadata(String key) throws SQLException {
        try (Connection connection = manager.getConnection()) {
            @Cleanup
            PreparedStatement ps = connection.prepareStatement("SELECT * FROM metadata WHERE `key` = ? LIMIT 1");
            @Cleanup
            ResultSet set = ps.executeQuery();
            if (set.next()) {
                return set.getString("key");
            } else {
                return null;
            }
        }
    }

    public List<BanLog> queryBanLogs(Date from, Date to, int pageIndex, int pageSize) throws SQLException {
        try (Connection connection = manager.getConnection()) {
            PreparedStatement ps;
            if (from == null && to == null) {
                ps = connection.prepareStatement("SELECT * FROM ban_logs ORDER BY id DESC LIMIT " + (pageIndex * pageSize) + ", " + pageSize);
            } else {
                if (from == null || to == null) {
                    throw new IllegalArgumentException("from or null cannot be null if any provided");
                } else {
                    ps = connection.prepareStatement("SELECT * FROM ban_logs WHERE ban_at >= ? AND ban_at <= ? ORDER BY id DESC LIMIT " + (pageIndex * pageSize) + ", " + pageSize);
                }
            }
            try (ps) {
                if(from != null){
                    ps.setDate(1, from);
                    ps.setDate(2, to);
                }
                try (ResultSet set = ps.executeQuery()) {
                    List<BanLog> logs = new LinkedList<>(); // 尽可能节约内存，不使用 ArrayList
                    while (set.next()) {
                        BanLog banLog = new BanLog(
                                set.getLong("ban_at"),
                                set.getLong("unban_at"),
                                set.getString("peer_ip"),
                                set.getInt("peer_port"),
                                set.getString("peer_id"),
                                set.getString("peer_clientname"),
                                set.getLong("peer_uploaded"),
                                set.getLong("peer_downloaded"),
                                set.getDouble("peer_progress"),
                                set.getString("torrent_infohash"),
                                set.getString("torrent_name"),
                                set.getLong("torrent_size"),
                                set.getString("module"),
                                set.getString("description")
                        );
                        logs.add(banLog);
                    }
                    return logs;
                }
            }
        }
    }

    public Map<String, Long> findMaxBans(int n) throws SQLException {
        try(Connection connection = manager.getConnection()){
            @Cleanup
            PreparedStatement ps = connection.prepareStatement("SELECT peer_ip, COUNT(*) AS count " +
                    "FROM ban_logs " +
                    "GROUP BY peer_ip " +
                    "ORDER BY count DESC LIMIT "+n);
            @Cleanup
            ResultSet set  = ps.executeQuery();
            Map<String, Long> map = new LinkedHashMap<>();
            while (set.next()){
                map.put(set.getString("peer_ip"), set.getLong("count"));
            }
            return map;
        }
    }

    public int insertBanLogs(List<BanLog> banLogList) throws SQLException {
        if(banLogList.isEmpty()){
            return 0;
        }
        try (Connection connection = manager.getConnection()) {
            @Cleanup
            PreparedStatement ps = connection.prepareStatement("INSERT INTO ban_logs (ban_at, unban_at, peer_ip, peer_port, peer_id, peer_clientname," +
                    " peer_downloaded, peer_uploaded, peer_progress, torrent_infohash, torrent_name, torrent_size, module, description)" +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
            for (BanLog banLog : banLogList) {
                ps.setLong(1, banLog.banAt());
                ps.setLong(2, banLog.unbanAt());
                ps.setString(3, banLog.peerIp());
                ps.setInt(4, banLog.peerPort());
                ps.setString(5, banLog.peerId());
                ps.setString(6, banLog.peerClientName());
                ps.setLong(7, banLog.peerDownloaded());
                ps.setLong(8, banLog.peerUploaded());
                ps.setDouble(9, banLog.peerProgress());
                ps.setString(10, banLog.torrentInfoHash());
                ps.setString(11, banLog.torrentName());
                ps.setLong(12, banLog.torrentSize());
                ps.setString(13, banLog.module());
                ps.setString(14, banLog.description());
                ps.addBatch();
            }
            return ps.executeUpdate();
        }
    }

    public void createTables() throws SQLException {
        try (Connection connection = manager.getConnection()) {
            if (!hasTable("ban_logs")) {
                @Cleanup
                PreparedStatement ps = connection.prepareStatement("""
                                            CREATE TABLE ban_logs (
                                              "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                                              "ban_at" integer NOT NULL,
                                              "unban_at" integer NOT NULL,
                                              "peer_ip" TEXT NOT NULL,
                                              "peer_port" integer NOT NULL,
                                              "peer_id" text NOT NULL,
                                              "peer_clientname" TEXT NOT NULL,
                                              "peer_downloaded" integer NOT NULL,
                                              "peer_uploaded" integer NOT NULL,
                                              "peer_progress" real NOT NULL,
                                              "torrent_infohash" TEXT NOT NULL,
                                              "torrent_name" TEXT NOT NULL,
                                              "torrent_size" integer NOT NULL,
                                              "module" TEXT,
                                              "description" TEXT NOT NULL
                                            );
                        """);
                ps.executeUpdate();
                ps = connection.prepareStatement("""
                                        CREATE INDEX ban_logs_idx
                                        ON ban_logs (
                                          "id",
                                          "ban_at",
                                          "peer_ip",
                                          "torrent_name",
                                          "torrent_infohash",
                                          "module"
                                        );
                        """);
                ps.executeUpdate();
            }
            if (!hasTable("metadata")) {
                @Cleanup
                PreparedStatement ps = connection.prepareStatement("""
                                            CREATE TABLE metadata (
                                              "key" TEXT NOT NULL PRIMARY KEY,
                                              "value" TEXT
                                            );
                        """);
                ps.executeUpdate();
            }
        }
    }


    /**
     * Returns true if the given table has the given column
     *
     * @param table  The table
     * @param column The column
     * @return True if the given table has the given column
     * @throws SQLException If the database isn't connected
     */
    public boolean hasColumn(@NotNull String table, @NotNull String column) throws SQLException {
        if (!hasTable(table)) {
            return false;
        }
        String query = "SELECT * FROM " + table + " LIMIT 1";
        boolean match = false;
        try (Connection connection = manager.getConnection(); PreparedStatement ps = connection.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData metaData = rs.getMetaData();
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                if (metaData.getColumnLabel(i).equals(column)) {
                    match = true;
                    break;
                }
            }
        } catch (SQLException e) {
            return match;
        }
        return match; // Uh, wtf.
    }


    /**
     * Returns true if the table exists
     *
     * @param table The table to check for
     * @return True if the table is found
     * @throws SQLException Throw exception when failed execute somethins on SQL
     */
    public boolean hasTable(@NotNull String table) throws SQLException {
        Connection connection = manager.getConnection();
        boolean match = false;
        try (ResultSet rs = connection.getMetaData().getTables(null, null, "%", null)) {
            while (rs.next()) {
                if (table.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                    match = true;
                    break;
                }
            }
        } finally {
            connection.close();
        }
        return match;
    }

}
