package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.PeerNameRuleSubInfoEntity;
import com.j256.ormlite.support.ConnectionSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
public final class PeerNameRuleSubInfoDao extends AbstractPBHDao<PeerNameRuleSubInfoEntity, String> {
    public PeerNameRuleSubInfoDao(@Autowired ConnectionSource database) throws SQLException {
        super(database, PeerNameRuleSubInfoEntity.class);
    }
}
