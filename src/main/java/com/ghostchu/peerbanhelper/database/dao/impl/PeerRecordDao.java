package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.PeerRecordEntity;
import com.ghostchu.peerbanhelper.database.table.TorrentEntity;
import com.ghostchu.peerbanhelper.util.query.Page;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.ghostchu.peerbanhelper.wrapper.PeerWrapper;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.support.ConnectionSource;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Deque;

@Component
@Slf4j
public final class PeerRecordDao extends AbstractPBHDao<PeerRecordEntity, Long> {

    public PeerRecordDao(@Autowired ConnectionSource database) throws SQLException {
        super(database, PeerRecordEntity.class);
    }

    public void syncPendingTasks(Deque<BatchHandleTasks> tasks) throws SQLException {
        callBatchTasks(() -> {
            while (!tasks.isEmpty()) {
                var t = tasks.pop();
                try {
                    writeToDatabase(torrentDao, t.timestamp, t.downloader, t.torrent, t.peer);
                } catch (SQLException e) {
                    log.error("Unable save peer record to database, please report to developer: {}, {}, {}, {}", t.timestamp, t.downloader, t.torrent, t.peer);
                    Sentry.captureException(e);
                }
            }
            return null;
        });
    }

    public long sessionBetween(@NotNull String downloader, @NotNull Timestamp startAt, @NotNull Timestamp endAt) throws SQLException {
        // 从 startAt 到 endAt，每天的开始时间戳
        var queryBuilder = queryBuilder();
        var where = queryBuilder
                .selectColumns("address")
                .distinct()
                .where();
        where.and(where.like("downloader", downloader), where.or(where.between("firstTimeSeen", startAt, endAt),
                where.between("lastTimeSeen", startAt, endAt)));
        return queryBuilder.countOf();
    }

    private int writeToDatabase(long timestamp, String downloader, TorrentWrapper torrent, PeerWrapper peer) throws SQLException {
        TorrentEntity torrentEntity = torrentDao.createIfNotExists(new TorrentEntity(
                null,
                torrent.getHash(),
                torrent.getName(),
                torrent.getSize(),
                torrent.isPrivateTorrent()
        ));
        PeerRecordEntity currentSnapshot = new PeerRecordEntity(
                null,
                peer.toPeerAddress().getAddress().toNormalizedString(),
                peer.toPeerAddress().getPort(),
                torrentEntity,
                downloader,
                peer.getId().length() > 8 ? peer.getId().substring(0, 8) : peer.getId(),
                peer.getClientName(),
                0,
                0,
                0,
                0,
                0,
                0,
                peer.getFlags(),
                new Timestamp(timestamp),
                new Timestamp(timestamp)
        );
        PeerRecordEntity databaseSnapshot = createIfNotExists(currentSnapshot);
        if (databaseSnapshot.getLastTimeSeen().after(new Timestamp(timestamp))) {
            return 0;
        }
        long downloadedIncremental = peer.getDownloaded() - databaseSnapshot.getDownloadedOffset();
        long uploadedIncremental = peer.getUploaded() - databaseSnapshot.getUploadedOffset();
        if (downloadedIncremental < 0 || uploadedIncremental < 0) {
            databaseSnapshot.setDownloaded(databaseSnapshot.getDownloaded() + peer.getDownloaded());
            databaseSnapshot.setUploaded(databaseSnapshot.getUploaded() + peer.getUploaded());
        } else {
            databaseSnapshot.setDownloaded(databaseSnapshot.getDownloaded() + downloadedIncremental);
            databaseSnapshot.setUploaded(databaseSnapshot.getUploaded() + uploadedIncremental);
        }
        // 更新 offset，转换为增量数据
        databaseSnapshot.setDownloadedOffset(peer.getDownloaded());
        databaseSnapshot.setUploadedOffset(peer.getUploaded());
        databaseSnapshot.setDownloadSpeed(peer.getDownloadSpeed());
        databaseSnapshot.setUploadSpeed(peer.getUploadSpeed());
        databaseSnapshot.setPeerId(currentSnapshot.getPeerId());
        databaseSnapshot.setClientName(currentSnapshot.getClientName());
        databaseSnapshot.setLastFlags(currentSnapshot.getLastFlags());
        databaseSnapshot.setLastTimeSeen(currentSnapshot.getLastTimeSeen());
        return update(databaseSnapshot);
    }

    public Page<PeerRecordEntity> getPendingSubmitPeerRecords(Pageable pageable, Timestamp afterThan) throws SQLException {
        var queryBuilder = queryBuilder().where()
                .gt("lastTimeSeen", afterThan)
                .or()
                .isNull("lastTimeSeen")
                .queryBuilder()
                .orderBy("lastTimeSeen", true);
        return queryByPaging(queryBuilder, pageable);
    }

    @Override
    public synchronized PeerRecordEntity createIfNotExists(PeerRecordEntity data) throws SQLException {
        PeerRecordEntity existing = queryBuilder().where()
                .eq("address", new SelectArg(data.getAddress()))
                .and()
                .eq("torrent_id", data.getTorrent().getId())
                .and()
                .eq("downloader", new SelectArg(data.getDownloader()))
                .queryForFirst();
        if (existing == null) {
            create(data);
            return data;
        } else {
            return existing;
        }
    }

    public record BatchHandleTasks(long timestamp, String downloader, TorrentWrapper torrent, PeerWrapper peer) {

    }

}
