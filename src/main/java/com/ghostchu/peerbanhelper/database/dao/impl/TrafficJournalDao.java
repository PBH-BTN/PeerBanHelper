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

    public TrafficJournalEntity getTodayJournal(String downloader) throws SQLException {
        return createIfNotExists(new TrafficJournalEntity(MiscUtil.getStartOfToday(System.currentTimeMillis()), downloader, 0, 0, 0, 0));
    }

    @Override
    public synchronized TrafficJournalEntity createIfNotExists(TrafficJournalEntity data) throws SQLException {
        if (data == null) {
            return null;
        }
        TrafficJournalEntity existing = queryBuilder().where()
                .eq("timestamp", data.getTimestamp())
                .and()
                .eq("downloader", data.getDownloader()).queryBuilder().queryForFirst();
        if (existing == null) {
            create(data);
            return data;
        } else {
            return existing;
        }
    }

}
