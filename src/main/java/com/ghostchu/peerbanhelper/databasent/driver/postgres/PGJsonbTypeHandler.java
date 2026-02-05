package com.ghostchu.peerbanhelper.databasent.driver.postgres;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@Slf4j
@MappedTypes({Object.class})
public class PGJsonbTypeHandler extends JacksonTypeHandler {

    public PGJsonbTypeHandler(Class<?> type) {
        super(type);
    }

    // 自3.5.6版本开始支持泛型,需要加上此构造.
    public PGJsonbTypeHandler(Class<?> type, Field field) {
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
        return JsonUtil.standard().toJson(obj);
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