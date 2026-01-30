package com.ghostchu.peerbanhelper.databasent.driver.common;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeHandler;

import java.net.InetAddress;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Slf4j
@MappedTypes({InetAddress.class})
public class BasicInetTypeHandler implements TypeHandler<InetAddress> {
    public static final BasicInetTypeHandler INSTANCE = new BasicInetTypeHandler();

    @Override
    public void setParameter(PreparedStatement ps, int i, InetAddress parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.getHostAddress());
    }

    @Override
    public InetAddress getResult(ResultSet rs, String columnName) throws SQLException {
        return InetAddress.ofLiteral(rs.getString(columnName));
    }

    @Override
    public InetAddress getResult(ResultSet rs, int columnIndex) throws SQLException {
        return InetAddress.ofLiteral(rs.getString(columnIndex));
    }

    @Override
    public InetAddress getResult(CallableStatement cs, int columnIndex) throws SQLException {
        return InetAddress.ofLiteral(cs.getString(columnIndex));
    }
}
