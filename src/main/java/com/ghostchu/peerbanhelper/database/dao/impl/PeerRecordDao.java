package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.PeerRecordEntity;
import com.j256.ormlite.support.ConnectionSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
@Slf4j
public final class PeerRecordDao extends AbstractPBHDao<PeerRecordEntity, Long> {

    public PeerRecordDao(@Autowired ConnectionSource database) throws SQLException {
        super(database, PeerRecordEntity.class);
    }




}
