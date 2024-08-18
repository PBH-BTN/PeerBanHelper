package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.TrafficJournalEntity;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
public class TrafficJournalDao extends AbstractPBHDao<TrafficJournalEntity, Long> {
    public TrafficJournalDao(@Autowired Database database) throws SQLException {
        super(database.getDataSource(), TrafficJournalEntity.class);
    }

    public TrafficJournalEntity getTodayJourney() throws SQLException {
        return createIfNotExists(new TrafficJournalEntity(MiscUtil.getStartOfToday(System.currentTimeMillis()), 0, 0));
    }

}
