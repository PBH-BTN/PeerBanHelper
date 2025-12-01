package com.ghostchu.peerbanhelper.database.dao.impl.tmp;

import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.tmp.TrackedSwarmEntity;
import com.ghostchu.peerbanhelper.util.query.Page;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.j256.ormlite.support.ConnectionSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
public final class TrackedSwarmDao extends AbstractPBHDao<TrackedSwarmEntity, Long> {


    public TrackedSwarmDao(@Autowired ConnectionSource source) throws SQLException {
        super(source, TrackedSwarmEntity.class);
    }

    public Page<TrackedSwarmEntity> getPendingSubmitTrackedPeers(Pageable pageable, long idAfterThan) throws SQLException {
        var queryBuilder = queryBuilder().where()
                .gt("id", idAfterThan)
                .queryBuilder()
                .orderBy("id", true);
        return queryByPaging(queryBuilder, pageable);
    }
}
