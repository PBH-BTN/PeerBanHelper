package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.MetadataEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;

@Component
public final class MetadataDao extends AbstractPBHDao<MetadataEntity, Long> {
    public MetadataDao(@Autowired Database database) throws SQLException {
        super(database.getDataSource(), MetadataEntity.class);
    }

    @Override
    public synchronized MetadataEntity createIfNotExists(MetadataEntity data) throws SQLException {
        List<MetadataEntity> list = queryForMatchingArgs(data);
        if (list.isEmpty()) {
            return super.createIfNotExists(data);
        }
        return list.getFirst();
    }
}
