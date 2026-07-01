package com.ghostchu.peerbanhelper.databasent.driver.common;

import com.ghostchu.peerbanhelper.configuration.DatabaseDriverConfig;
import com.ghostchu.peerbanhelper.databasent.DatabaseType;
import com.ghostchu.peerbanhelper.databasent.driver.common.basic.BasicInetTypeHandler;
import com.ghostchu.peerbanhelper.databasent.driver.postgres.PGInetAddressTypeHandler;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeHandler;

import java.net.InetAddress;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes({InetAddress.class})
public class InetTypeHandlerForwarder extends BaseTypeHandler<InetAddress> {
    private TypeHandler<InetAddress> handler;

    private void prepareTypeHandler() {
        if (handler == null) {
            if (DatabaseDriverConfig.databaseDriver.getType() == DatabaseType.POSTGRES) {
                handler = new PGInetAddressTypeHandler();
            } else {
                handler = new BasicInetTypeHandler();
            }
        }
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, InetAddress parameter, JdbcType jdbcType) throws SQLException {
        prepareTypeHandler();
        handler.setParameter(ps, i, parameter, jdbcType);
    }

    @Override
    public InetAddress getNullableResult(ResultSet rs, String columnName) throws SQLException {
        prepareTypeHandler();
        return handler.getResult(rs, columnName);
    }

    @Override
    public InetAddress getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        prepareTypeHandler();
        return handler.getResult(rs, columnIndex);
    }

    @Override
    public InetAddress getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        prepareTypeHandler();
        return handler.getResult(cs, columnIndex);
    }
}

