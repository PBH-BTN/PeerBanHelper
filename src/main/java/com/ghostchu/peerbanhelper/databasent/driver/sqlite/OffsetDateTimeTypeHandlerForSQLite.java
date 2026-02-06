package com.ghostchu.peerbanhelper.databasent.driver.sqlite;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * TypeHandler for OffsetDateTime in SQLite.
 * SQLite stores datetime as INTEGER (millisecond UNIX timestamp).
 */
public class OffsetDateTimeTypeHandlerForSQLite extends BaseTypeHandler<OffsetDateTime> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, OffsetDateTime parameter, JdbcType jdbcType)
            throws SQLException {
        // Convert OffsetDateTime to millisecond timestamp
        long epochMilli = parameter.toInstant().toEpochMilli();
        ps.setLong(i, epochMilli);
    }

    @Override
    public OffsetDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        long epochMilli = rs.getLong(columnName);
        return parseOffsetDateTime(epochMilli, rs.wasNull());
    }

    @Override
    public OffsetDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        long epochMilli = rs.getLong(columnIndex);
        return parseOffsetDateTime(epochMilli, rs.wasNull());
    }

    @Override
    public OffsetDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        long epochMilli = cs.getLong(columnIndex);
        return parseOffsetDateTime(epochMilli, cs.wasNull());
    }

    private OffsetDateTime parseOffsetDateTime(long epochMilli, boolean wasNull) {
        if (wasNull) {
            return null;
        }
        // Convert millisecond timestamp to OffsetDateTime with system default timezone
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }
}
