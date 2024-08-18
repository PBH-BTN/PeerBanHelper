package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.TorrentEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class TorrentDao extends AbstractPBHDao<TorrentEntity, Long> {
    public TorrentDao(@Autowired Database database) throws SQLException {
        super(database.getDataSource(), TorrentEntity.class);
        setObjectCache(true);
    }

    public Optional<TorrentEntity> queryByInfoHash(String infoHash) throws SQLException {
        var torrent = queryBuilder()
                .limit(1L)
                .where()
                .eq("infoHash", infoHash)
                .queryForFirst();
        return Optional.ofNullable(torrent);
    }

    @Override
    public synchronized TorrentEntity createIfNotExists(TorrentEntity data) throws SQLException {
        List<TorrentEntity> list = queryForEq("infoHash", data.getInfoHash());
        if (list.isEmpty()) {
            long id = create(data);
            data.setId(id);
            return data;
        }
        TorrentEntity entity = list.getFirst();
        boolean anyUpdated = false;
        if (!entity.getName().equals(data.getName())) {
            entity.setName(data.getName());
            anyUpdated = true;
        }
        if (!(Objects.equals(entity.getSize(), data.getSize()))) {
            entity.setSize(data.getSize());
            anyUpdated = true;
        }
        if (anyUpdated) {
            update(entity);
        }
        return entity;
    }
}
