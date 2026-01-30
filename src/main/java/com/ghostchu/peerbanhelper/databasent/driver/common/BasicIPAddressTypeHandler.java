package com.ghostchu.peerbanhelper.databasent.driver.common;

import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import inet.ipaddr.IPAddress;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Slf4j
@MappedTypes({IPAddress.class})
public class BasicIPAddressTypeHandler extends BaseTypeHandler<IPAddress> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, IPAddress parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.toNormalizedString());
    }

    @Override
    public IPAddress getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String inetString = rs.getString(columnName);
        return parseInetAddress(inetString);
    }

    @Override
    public IPAddress getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String inetString = rs.getString(columnIndex);
        return parseInetAddress(inetString);
    }

    @Override
    public IPAddress getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String inetString = cs.getString(columnIndex);
        return parseInetAddress(inetString);
    }

    private IPAddress parseInetAddress(String inetString) {
        if (inetString == null) return null;
        try {
            return IPAddressUtil.getIPAddress(inetString);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid inet address: " + inetString, e);
        }
    }
}