package com.ghostchu.peerbanhelper.database.dao;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.table.RuleEntity;
import com.j256.ormlite.dao.BaseDaoImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;

@Component
public class RuleDao extends BaseDaoImpl<RuleEntity, Long> {
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
