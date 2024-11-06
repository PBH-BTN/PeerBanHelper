package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.ProgressCheatBlockerPersistEntity;
import com.ghostchu.peerbanhelper.module.impl.rule.ProgressCheatBlocker;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.j256.ormlite.stmt.SelectArg;
import inet.ipaddr.IPAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ProgressCheatBlockerPersistDao extends AbstractPBHDao<ProgressCheatBlockerPersistEntity, Long> {
    public ProgressCheatBlockerPersistDao(@Autowired Database database) throws SQLException {
        super(database.getDataSource(), ProgressCheatBlockerPersistEntity.class);
    }

    public List<ProgressCheatBlocker.ClientTask> fetchFromDatabase(ProgressCheatBlocker.Client client, Timestamp after) throws SQLException {
        IPAddress address = IPAddressUtil.getIPAddress(client.getPeerPrefix());
        if (address == null) return Collections.emptyList();
        List<ProgressCheatBlockerPersistEntity> entities = queryBuilder()
                .where()
                .eq("torrentId", new SelectArg(client.getTorrentId()))
                .and()
                .ge("lastTimeSeen", after)
                .query();
        return entities.stream().filter(
                entity -> address.contains(IPAddressUtil.getIPAddress(entity.getAddress()))
        ).map(
                entity -> new ProgressCheatBlocker.ClientTask(
                        entity.getAddress(),
                        entity.getLastReportProgress(),
                        entity.getLastReportUploaded(),
                        entity.getTrackingUploadedIncreaseTotal(),
                        entity.getRewindCounter(),
                        entity.getProgressDifferenceCounter(),
                        entity.getFirstTimeSeen().getTime(),
                        entity.getLastTimeSeen().getTime(),
                        entity.getDownloader(),
                        entity.getBanDelayWindowEndAt().getTime(),
                        entity.getFastPcbTestExecuteAt()
                )
        ).collect(Collectors.toCollection(CopyOnWriteArrayList::new)); // 可变 List，需要并发安全
    }

    public void flushDatabase(Deque<ProgressCheatBlocker.ClientTaskRecord> records) throws SQLException {
        callBatchTasks(() -> {
            while (!records.isEmpty()) {
                var record = records.pop();
                String torrentId = record.client().getTorrentId();
                record.task().forEach(task -> {
                    try {
                        var entity = findExists(task.getPeerIp(), torrentId);
                        if (entity == null) {
                            entity = new ProgressCheatBlockerPersistEntity(null,
                                    task.getPeerIp(),
                                    torrentId,
                                    task.getLastReportProgress(),
                                    task.getLastReportUploaded(),
                                    task.getTrackingUploadedIncreaseTotal(),
                                    task.getRewindCounter(),
                                    task.getProgressDifferenceCounter(),
                                    new Timestamp(System.currentTimeMillis()),
                                    new Timestamp(System.currentTimeMillis()),
                                    task.getDownloader(),
                                    new Timestamp(task.getBanDelayWindowEndAt()),
                                    task.getFastPcbTestExecuteAt()
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
                });
            }
            return null;
        });
    }

    private ProgressCheatBlockerPersistEntity findExists(String ip, String torrentId) throws SQLException {
        return queryBuilder()
                .where()
                .eq("address", new SelectArg(ip))
                .and()
                .eq("torrentId", new SelectArg(torrentId))
                .queryForFirst();
    }

    public int cleanupDatabase(Timestamp timestamp) throws SQLException {
        var builder = deleteBuilder();
        var where = builder.where().lt("lastTimeSeen", timestamp);
        builder.setWhere(where);
        return builder.delete();
    }
}
