package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.RuleEntity;
import com.j256.ormlite.support.ConnectionSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;

@Component
public final class RuleDao extends AbstractPBHDao<RuleEntity, Long> {
    public RuleDao(@Autowired ConnectionSource database) throws SQLException {
        super(database, RuleEntity.class);
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
