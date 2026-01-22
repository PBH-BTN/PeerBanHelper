package com.ghostchu.peerbanhelper.databasent.driver.postgres;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@Slf4j
@MappedTypes({Object.class})
public class JsonbTypeHandler extends JacksonTypeHandler {
    public JsonbTypeHandler(Class<?> type) {
        super(type);
    }

    // 自3.5.6版本开始支持泛型,需要加上此构造.
    public JsonbTypeHandler(Class<?> type, Field field) {
        super(type, field);
    }

    @Override
    public Object parse(String json) {
        TypeFactory typeFactory = getObjectMapper().getTypeFactory();
        JavaType javaType = typeFactory.constructType(getFieldType());
        try {
            return getObjectMapper().readValue(json, javaType);
        } catch (JacksonException e) {
            log.error("deserialize json: " + json + " to " + javaType + " error ", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toJson(Object obj) {
        try {
            return getObjectMapper().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("serialize " + obj + " to json error ", e);
            throw new RuntimeException(e);
        }
    }


    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        if (ps != null) {
            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb");
            jsonObject.setValue(toJson(parameter));
            ps.setObject(i, jsonObject);
        }
    }
}