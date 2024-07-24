package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.ProgressCheatBlockerPersistEntity;
import com.ghostchu.peerbanhelper.module.impl.rule.ProgressCheatBlocker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ProgressCheatBlockerPersistDao extends AbstractPBHDao<ProgressCheatBlockerPersistEntity, Long> {
    public ProgressCheatBlockerPersistDao(@Autowired Database database) throws SQLException {
        super(database.getDataSource(), ProgressCheatBlockerPersistEntity.class);
    }

    public List<ProgressCheatBlocker.ClientTask> fetchFromDatabase(String address, String torrentId, Timestamp after) throws SQLException {
        List<ProgressCheatBlockerPersistEntity> entities = queryBuilder()
                .where()
                .eq("address", address)
                .and()
                .eq("torrentId", torrentId)
                .and()
                .ge("lastTimeSeen", after)
                .query();
        return entities.stream().map(
                entity -> new ProgressCheatBlocker.ClientTask(
                        entity.getTorrentId(),
                        entity.getLastReportProgress(),
                        entity.getLastReportUploaded(),
                        entity.getTrackingUploadedIncreaseTotal(),
                        entity.getRewindCounter(),
                        entity.getProgressDifferenceCounter(),
                        entity.getFirstTimeSeen().getTime(),
                        entity.getLastTimeSeen().getTime()
                )
        ).collect(Collectors.toList()); // 可变 List
    }

    public void flushDatabase(List<ProgressCheatBlocker.ClientTaskRecord> records) throws SQLException {
        callBatchTasks(() -> {
            records.forEach(record -> record.task().forEach(task -> {
                try {
                    var entity = findExists(record.address(), task.getTorrentId());
                    if (entity == null) {
                        entity = new ProgressCheatBlockerPersistEntity(null,
                                record.address(),
                                task.getTorrentId(),
                                task.getLastReportProgress(),
                                task.getLastReportUploaded(),
                                task.getTrackingUploadedIncreaseTotal(),
                                task.getRewindCounter(),
                                task.getProgressDifferenceCounter(),
                                new Timestamp(System.currentTimeMillis()),
                                new Timestamp(System.currentTimeMillis())
                        );
                        create(entity);
                    } else {
                        entity.setLastReportProgress(task.getLastReportProgress());
                        entity.setLastReportUploaded(task.getLastReportUploaded());
                        entity.setProgressDifferenceCounter(task.getProgressDifferenceCounter());
                        entity.setRewindCounter(task.getRewindCounter());
                        entity.setTrackingUploadedIncreaseTotal(task.getTrackingUploadedIncreaseTotal());
                        entity.setLastTimeSeen(new Timestamp(System.currentTimeMillis()));
                        update(entity);
                    }
                } catch (SQLException e) {
                    log.error("Unable write PCB persist data into database", e);
                }
            }));
            return null;
        });
    }

    private ProgressCheatBlockerPersistEntity findExists(String ip, String torrentId) throws SQLException {
        return queryBuilder()
                .where()
                .eq("address", ip)
                .and()
                .eq("torrentId", torrentId)
                .queryForFirst();
    }

    public int cleanupDatabase(Timestamp timestamp) throws SQLException {
        var builder = deleteBuilder();
        var where = builder.where().lt("lastSeenTime", timestamp);
        builder.setWhere(where);
        return builder.delete();
    }
}
