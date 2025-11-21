package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.PCBAddressEntity;
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
public final class PCBAddressDao extends AbstractPBHDao<PCBAddressEntity, Long> {
    public PCBAddressDao(@Autowired ConnectionSource database) throws SQLException {
        super(database, PCBAddressEntity.class);
    }

    public List<PCBAddressEntity> fetchFromDatabase(@NotNull String torrentId, @NotNull String downloader) throws SQLException {
        return queryBuilder()
                .where()
                .eq("torrentId", new SelectArg(torrentId))
                .and()
                .eq("downloader", new SelectArg(downloader))
                .query();
    }

    public PCBAddressEntity fetchFromDatabase(@NotNull String torrentId, @NotNull String ip, int port, @NotNull String downloader) throws SQLException {
        return queryBuilder()
                .where()
                .eq("torrentId", new SelectArg(torrentId))
                .and()
                .eq("ip", new SelectArg(ip))
                .and()
                .eq("port", port)
                .and()
                .eq("downloader", new SelectArg(downloader))
                .queryForFirst();
    }

    public int deleteEntry(@NotNull String torrentId, @NotNull String ip) throws SQLException {
        var deleteBuilder = deleteBuilder();
        deleteBuilder.where().eq("torrentId", new SelectArg(torrentId)).and().eq("ip", new SelectArg(ip));
        return deleteBuilder.delete();
    }

    public int cleanupDatabase(Timestamp timestamp) throws SQLException {
        var builder = deleteBuilder();
        builder.where().lt("lastTimeSeen", timestamp);
        return builder.delete();
    }
}
