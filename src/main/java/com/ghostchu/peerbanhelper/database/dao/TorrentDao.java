package com.ghostchu.peerbanhelper.database.dao;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.table.TorrentEntity;
import com.j256.ormlite.dao.BaseDaoImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;

@Component
public class TorrentDao extends BaseDaoImpl<TorrentEntity, Long> {
    public TorrentDao(@Autowired Database database) throws SQLException {
        super(database.getDataSource(), TorrentEntity.class);
    }

    @Override
    public synchronized TorrentEntity createIfNotExists(TorrentEntity data) throws SQLException {
        List<TorrentEntity> list = queryForMatchingArgs(data);
        if (list.isEmpty()) {
            return super.createIfNotExists(data);
        }
        return list.getFirst();
    }
}
