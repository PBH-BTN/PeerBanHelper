package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.ModuleEntity;
import com.j256.ormlite.support.ConnectionSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;

@Component
public final class ModuleDao extends AbstractPBHDao<ModuleEntity, Long> {
    public ModuleDao(@Autowired ConnectionSource database) throws SQLException {
        super(database, ModuleEntity.class);
        setObjectCache(true);
    }

    @Override
    public synchronized ModuleEntity createIfNotExists(ModuleEntity data) throws SQLException {
        List<ModuleEntity> list = queryForMatchingArgs(data);
        if (list.isEmpty()) {
            return super.createIfNotExists(data);
        }
        return list.getFirst();
    }
}
