/**
 * Copyright 2009-2017 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ghostchu.peerbanhelper.databasent.driver.common;

import com.ghostchu.peerbanhelper.configuration.DatabaseDriverConfig;
import com.ghostchu.peerbanhelper.databasent.DatabaseType;
import org.apache.ibatis.lang.UsesJava8;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.OffsetDateTimeTypeHandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

/**
 * @author Tomas Rohovsky
 * @since 3.4.5
 */
@UsesJava8
public class OffsetDateTimeTypeHandlerForwarder extends BaseTypeHandler<OffsetDateTime> {
    private BaseTypeHandler<OffsetDateTime> handler;

    private void prepareTypeHandler() {
        if (handler == null) {
            if (DatabaseDriverConfig.databaseDriver.getType() == DatabaseType.MYSQL) {
                handler = new OffsetDateTimeTypeHandlerForMySQL();
            } else {
                handler = new OffsetDateTimeTypeHandler();
            }
        }
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, OffsetDateTime parameter, JdbcType jdbcType)
            throws SQLException {
        prepareTypeHandler();
        handler.setNonNullParameter(ps, i, parameter, jdbcType);
    }

    @Override
    public OffsetDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        prepareTypeHandler();
        return handler.getNullableResult(rs, columnName);
    }

    @Override
    public OffsetDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        prepareTypeHandler();
        return handler.getNullableResult(rs, columnIndex);
    }

    @Override
    public OffsetDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        prepareTypeHandler();
        return handler.getNullableResult(cs, columnIndex);
    }
}