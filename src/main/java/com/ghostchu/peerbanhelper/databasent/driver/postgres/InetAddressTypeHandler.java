package com.ghostchu.peerbanhelper.databasent.driver.postgres;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.net.InetAddress;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class InetAddressTypeHandler extends BaseTypeHandler<InetAddress> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, InetAddress parameter, JdbcType jdbcType) throws SQLException {
        PGobject pgObject = new PGobject();
        pgObject.setType("inet");
        pgObject.setValue(parameter.getHostAddress());
        ps.setObject(i, pgObject);
    }

    @Override
    public InetAddress getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String inetString = rs.getString(columnName);
        return parseInetAddress(inetString);
    }

    @Override
    public InetAddress getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String inetString = rs.getString(columnIndex);
        return parseInetAddress(inetString);
    }

    @Override
    public InetAddress getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String inetString = cs.getString(columnIndex);
        return parseInetAddress(inetString);
    }

    private InetAddress parseInetAddress(String inetString) {
        if (inetString == null) return null;
        try {
            return InetAddress.ofLiteral(inetString);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid inet address: " + inetString, e);
        }
    }
}