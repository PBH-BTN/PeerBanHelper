package com.ghostchu.peerbanhelper.database.dao.impl.tmp;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.tmp.TrackedPeerEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
public final class TrackerPeersDao extends AbstractPBHDao<TrackedPeerEntity, Long> {
    public TrackerPeersDao(@Autowired Database database) throws SQLException {
        super(database.getDataSource(), TrackedPeerEntity.class);
    }
}
