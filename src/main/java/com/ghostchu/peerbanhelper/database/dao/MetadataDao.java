package com.ghostchu.peerbanhelper.database.dao;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.table.MetadataEntity;
import com.j256.ormlite.dao.BaseDaoImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;

@Component
public class MetadataDao extends BaseDaoImpl<MetadataEntity, Long> {
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
