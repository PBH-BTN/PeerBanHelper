package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.PCBRangeEntity;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.support.ConnectionSource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Component
@Slf4j
public final class PCBRangeDao extends AbstractPBHDao<PCBRangeEntity, Long> {
    public PCBRangeDao(@Autowired ConnectionSource database) throws SQLException {
        super(database, PCBRangeEntity.class);
    }

    public List<PCBRangeEntity> fetchFromDatabase(@NotNull String torrentId, @NotNull String downloader) throws SQLException {
        return queryBuilder()
                .where()
                .eq("torrentId", new SelectArg(torrentId))
                .and()
                .eq("downloader", new SelectArg(downloader))
                .query();
    }

    public PCBRangeEntity fetchFromDatabase(@NotNull String torrentId, @NotNull String range, @NotNull String downloader) throws SQLException {
        return queryBuilder()
                .where()
                .eq("torrentId", new SelectArg(torrentId))
                .and()
                .eq("range", new SelectArg(range))
                .and()
                .eq("downloader", new SelectArg(downloader))
                .queryForFirst();
    }

    public int deleteEntry(@NotNull String torrentId, @NotNull String range) throws SQLException {
        var deleteBuilder = deleteBuilder();
        var where = deleteBuilder
                .where()
                .eq("torrentId", new SelectArg(torrentId))
                .and()
                .eq("range", new SelectArg(range));
        deleteBuilder.setWhere(where);
        return deleteBuilder.delete();
    }

    public int cleanupDatabase(Timestamp timestamp) throws SQLException {
        var builder = deleteBuilder();
        var where = builder.where().lt("lastTimeSeen", timestamp);
        builder.setWhere(where);
        return builder.delete();
    }
}
