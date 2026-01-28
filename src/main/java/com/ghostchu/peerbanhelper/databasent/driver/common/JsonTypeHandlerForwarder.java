package com.ghostchu.peerbanhelper.databasent.driver.common;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.ghostchu.peerbanhelper.configuration.DatabaseDriverConfig;
import com.ghostchu.peerbanhelper.databasent.DatabaseType;
import com.ghostchu.peerbanhelper.databasent.driver.postgres.PGJsonbTypeHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@Slf4j
@MappedTypes({Object.class})
public class JsonTypeHandlerForwarder extends JacksonTypeHandler {

    private JacksonTypeHandler typeHandler;

    public JsonTypeHandlerForwarder(Class<?> type) {
        super(type);
    }

    public JsonTypeHandlerForwarder(Class<?> type, Field field) {
        super(type, field);
    }

    public void prepareTypeHandler() {
        if (typeHandler == null) {
            if (DatabaseDriverConfig.databaseDriver.getType() == DatabaseType.POSTGRES) {
                typeHandler = new PGJsonbTypeHandler(type);
            } else {
                typeHandler = new BasicJsonTypeHandler(type);
            }
        }
    }

    @Override
    public Object parse(String json) {
        prepareTypeHandler();
        return typeHandler.parse(json);
    }

    @Override
    public String toJson(Object obj) {
        prepareTypeHandler();
        return typeHandler.toJson(obj);
    }


    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        prepareTypeHandler();
        typeHandler.setNonNullParameter(ps, i, parameter, jdbcType);
    }
}