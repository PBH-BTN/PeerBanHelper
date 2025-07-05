package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.AlertEntity;
import com.ghostchu.peerbanhelper.util.query.Page;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.support.ConnectionSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Component
@Slf4j
public final class AlertDao extends AbstractPBHDao<AlertEntity, Long> {
    public AlertDao(@Autowired ConnectionSource database) throws SQLException {
        super(database, AlertEntity.class);
    }

    public Page<AlertEntity> getUnreadAlerts(Pageable pageable) throws SQLException {
        return queryByPaging(queryBuilder().orderBy("createAt", false).where().isNull("readAt").queryBuilder(), pageable);
    }

    public List<AlertEntity> getUnreadAlertsUnPaged() throws SQLException {
        return queryBuilder().orderBy("createAt", false).where().isNull("readAt").queryBuilder().query();
    }


    public boolean identifierAlertExists(String identifier) throws SQLException {
        return queryForFirst(queryBuilder().where()
                .eq("identifier", new SelectArg(identifier)).and()
                .isNull("readAt")
                .prepare()) != null;
    }

    public boolean identifierAlertExistsIncludeRead(String identifier) throws SQLException {
        return queryForFirst(queryBuilder().where()
                .eq("identifier", new SelectArg(identifier))
                .prepare()) != null;
    }


    public int deleteOldAlerts(Timestamp before) throws SQLException {
        var builder = deleteBuilder();
        builder.setWhere(
                queryBuilder().where().lt("createAt",before)
                        .and()
                        .isNotNull("readAt")
        );
        return builder.delete();
    }


    public void markAllAsRead() throws SQLException {
        var alerts = queryBuilder().where().isNull("readAt")
                .query();
        var ts = new Timestamp(System.currentTimeMillis());
        for (AlertEntity alert : alerts) {
            alert.setReadAt(ts);
            update(alert);
        }
    }

    public void markAsRead(String identifier) throws SQLException {
        update(updateBuilder()
                .updateColumnValue("identifier", new SelectArg(identifier))
                .updateColumnValue("readAt", new Timestamp(System.currentTimeMillis()))
                .prepare());
    }
}