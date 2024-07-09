package com.ghostchu.peerbanhelper.database.dao;

import com.j256.ormlite.dao.BaseDaoImpl;
import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.support.ConnectionSource;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class AbstractPBHDao<T, ID> extends BaseDaoImpl<T, ID> {
    protected AbstractPBHDao(ConnectionSource connectionSource, Class<T> dataClass) throws SQLException {
        super(connectionSource, dataClass);
    }

    public List<T> queryByPaging(QueryBuilder<T, ID> qb, long offset, long limit) throws SQLException {
        return query(qb.offset(offset).limit(limit).prepare());
    }

    public List<T> queryByPaging(ID id, long offset, long limit) throws SQLException {
        return query(queryBuilder().offset(offset).limit(limit).prepare());
    }

    public List<T> queryByPaging(long offset, long limit) throws SQLException {
        return query(queryBuilder().offset(offset).limit(limit).prepare());
    }

    public List<T> queryByPagingMatchArgs(T matchObject, long offset, long limit) throws SQLException {
        checkForInitialized();
        QueryBuilder<T, ID> qb = queryBuilder();
        Where<T, ID> where = qb.where();
        int fieldC = 0;
        for (FieldType fieldType : tableInfo.getFieldTypes()) {
            Object fieldValue = fieldType.getFieldValueIfNotDefault(matchObject);
            if (fieldValue != null) {
                fieldValue = new SelectArg(fieldValue);
                where.eq(fieldType.getColumnName(), fieldValue);
                fieldC++;
            }
        }
        if (fieldC == 0) {
            return Collections.emptyList();
        } else {
            where.and(fieldC);
            return qb.offset(offset).limit(limit).query();
        }
    }
}
