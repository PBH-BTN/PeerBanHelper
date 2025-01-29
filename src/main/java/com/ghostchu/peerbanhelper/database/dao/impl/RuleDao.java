package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.RuleEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;

@Component
public final class RuleDao extends AbstractPBHDao<RuleEntity, Long> {
    public RuleDao(@Autowired Database database) throws SQLException {
        super(database.getDataSource(), RuleEntity.class);
    }

    @Override
    public synchronized RuleEntity createIfNotExists(RuleEntity data) throws SQLException {
        List<RuleEntity> list = queryForMatchingArgs(data);
        if (list.isEmpty()) {
            return super.createIfNotExists(data);
        }
        return list.getFirst();
    }
}
