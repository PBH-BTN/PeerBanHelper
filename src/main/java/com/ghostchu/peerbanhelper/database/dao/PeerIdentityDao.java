package com.ghostchu.peerbanhelper.database.dao;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.table.PeerIdentityEntity;
import com.j256.ormlite.dao.BaseDaoImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;

@Component
public class PeerIdentityDao extends BaseDaoImpl<PeerIdentityEntity, Long> {
    public PeerIdentityDao(@Autowired Database database) throws SQLException {
        super(database.getDataSource(), PeerIdentityEntity.class);
    }

    @Override
    public synchronized PeerIdentityEntity createIfNotExists(PeerIdentityEntity data) throws SQLException {
        List<PeerIdentityEntity> list = queryForMatchingArgs(data);
        if (list.isEmpty()) {
            return super.createIfNotExists(data);
        }
        return list.getFirst();
    }
}
