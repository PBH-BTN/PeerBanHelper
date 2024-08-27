package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.AlertEntity;
import com.ghostchu.peerbanhelper.util.paging.Page;
import com.ghostchu.peerbanhelper.util.paging.Pageable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
@Slf4j
public class AlertDao extends AbstractPBHDao<AlertEntity, Long> {
    public AlertDao(@Autowired Database database) throws SQLException {
        super(database.getDataSource(), AlertEntity.class);
    }

    public Page<AlertEntity> getUnreadAlerts(Pageable pageable) throws SQLException {
        return queryByPaging(queryBuilder().orderBy("createAt", false).where().isNull("readAt").queryBuilder(), pageable);
    }

    public boolean identifierAlertExists(String identifier) throws SQLException {
        return queryBuilder().where()
                .eq("identifier", identifier).and()
                .isNull("readAt")
                .queryForFirst() != null;
    }
}
