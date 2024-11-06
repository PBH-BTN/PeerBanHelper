package com.ghostchu.peerbanhelper.database.dao;

import com.ghostchu.peerbanhelper.util.paging.Page;
import com.ghostchu.peerbanhelper.util.paging.Pageable;
import com.j256.ormlite.dao.BaseDaoImpl;
import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.support.ConnectionSource;

import java.sql.SQLException;
import java.util.Collections;
import java.util.concurrent.Callable;

public class AbstractPBHDao<T, ID> extends BaseDaoImpl<T, ID> {
    private static final Object transactionLock = new Object();
    protected AbstractPBHDao(ConnectionSource connectionSource, Class<T> dataClass) throws SQLException {
        super(connectionSource, dataClass);
    }

    @Override
    public <CT> CT callBatchTasks(Callable<CT> callable) throws SQLException {
        synchronized (transactionLock) {
            return super.callBatchTasks(callable);
        }
    }

    public Page<T> queryByPaging(QueryBuilder<T, ID> qb, Pageable pageable) throws SQLException {
        var r = query(qb.offset(pageable.getZeroBasedPage() * pageable.getSize()).limit(pageable.getSize()).prepare());
        var ct = qb.offset(null).limit(null).countOf();
        return new Page<>(
                pageable,
                ct,
                r
        );
    }

    public Page<T> queryByPaging(Pageable pageable) throws SQLException {
        return new Page<>(
                pageable,
                countOf(),
                query(queryBuilder().offset(pageable.getZeroBasedPage() * pageable.getSize()).limit(pageable.getSize()).prepare())
        );
    }

    public Page<T> queryByPagingMatchArgs(T matchObject, Pageable pageable) throws SQLException {
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
            return new Page<>(pageable.getPage(), pageable.getSize(), 0, Collections.emptyList());
        } else {
            where.and(fieldC);
            var results = query(qb.offset(pageable.getZeroBasedPage() * pageable.getSize()).limit(pageable.getSize()).prepare());
            var ct = qb.countOf();
            return new Page<>(pageable, ct, results);
        }
    }
}
