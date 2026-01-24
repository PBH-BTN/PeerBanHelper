package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.RuleSubLogEntity;
import com.j256.ormlite.support.ConnectionSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
public final class RuleSubLogsDao extends AbstractPBHDao<RuleSubLogEntity, Long> {
    public RuleSubLogsDao(@Autowired ConnectionSource database) throws SQLException {
        super(database, RuleSubLogEntity.class);
    }
}
