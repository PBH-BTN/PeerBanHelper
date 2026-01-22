package com.ghostchu.peerbanhelper.databasent.converter;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(TranslationComponent.class)
@MappedJdbcTypes({JdbcType.VARCHAR, JdbcType.LONGVARCHAR, JdbcType.CLOB})
public final class TranslationComponentTypeHandler extends BaseTypeHandler<TranslationComponent> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, TranslationComponent parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, JsonUtil.standard().toJson(parameter));
    }

    @Override
    public TranslationComponent getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return parse(json);
    }

    @Override
    public TranslationComponent getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        return parse(json);
    }

    @Override
    public TranslationComponent getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        return parse(json);
    }

    private TranslationComponent parse(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return JsonUtil.standard().fromJson(json, TranslationComponent.class);
    }
}
