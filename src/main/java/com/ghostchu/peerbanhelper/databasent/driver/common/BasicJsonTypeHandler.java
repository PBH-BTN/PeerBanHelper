package com.ghostchu.peerbanhelper.databasent.driver.common;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@Slf4j
@MappedTypes({Object.class})
public class BasicJsonTypeHandler extends JacksonTypeHandler {

    public BasicJsonTypeHandler(Class<?> type) {
        super(type);
    }

    public BasicJsonTypeHandler(Class<?> type, Field field) {
        super(type, field);
    }

    @Override
    public Object parse(String json) {
        try {
            return JsonUtil.standard().fromJson(json, type);
        } catch (JsonSyntaxException e) {
            log.error("deserialize json: {} to {} error ", json, type, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toJson(Object obj) {
        try {
            return JsonUtil.standard().toJson(obj);
        } catch (JsonSyntaxException e) {
            log.error("serialize {} to json error ", obj, e);
            throw new RuntimeException(e);
        }
    }


    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        if (ps != null) {
            ps.setString(i, toJson(parameter));
        }
    }
}