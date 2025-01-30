package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.RuleSubInfoEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
public final class RuleSubInfoDao extends AbstractPBHDao<RuleSubInfoEntity, String> {
    public RuleSubInfoDao(@Autowired Database database) throws SQLException {
        super(database.getDataSource(), RuleSubInfoEntity.class);
    }
}
