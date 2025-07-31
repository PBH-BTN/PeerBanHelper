package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.table.EnhancedRuleSubInfoEntity;
import com.j256.ormlite.dao.BaseDaoImpl;
import com.j256.ormlite.support.ConnectionSource;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
public class EnhancedRuleSubInfoDao extends BaseDaoImpl<EnhancedRuleSubInfoEntity, String> {
    
    public EnhancedRuleSubInfoDao(ConnectionSource connectionSource) throws SQLException {
        super(connectionSource, EnhancedRuleSubInfoEntity.class);
    }
    
    protected EnhancedRuleSubInfoDao() throws SQLException {
        super(EnhancedRuleSubInfoEntity.class);
    }
}