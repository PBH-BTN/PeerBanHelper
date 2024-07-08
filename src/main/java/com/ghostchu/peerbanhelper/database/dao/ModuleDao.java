package com.ghostchu.peerbanhelper.database.dao;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.table.ModuleEntity;
import com.j256.ormlite.dao.BaseDaoImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;

@Component
public class ModuleDao extends BaseDaoImpl<ModuleEntity, Long> {
    public ModuleDao(@Autowired Database database) throws SQLException {
        super(database.getDataSource(), ModuleEntity.class);
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
