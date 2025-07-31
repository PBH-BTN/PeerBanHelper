package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.table.EnhancedRuleSubLogEntity;
import com.j256.ormlite.dao.BaseDaoImpl;
import com.j256.ormlite.support.ConnectionSource;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
public class EnhancedRuleSubLogDao extends BaseDaoImpl<EnhancedRuleSubLogEntity, Long> {
    
    public EnhancedRuleSubLogDao(ConnectionSource connectionSource) throws SQLException {
        super(connectionSource, EnhancedRuleSubLogEntity.class);
    }
    
    protected EnhancedRuleSubLogDao() throws SQLException {
        super(EnhancedRuleSubLogEntity.class);
    }
}