package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.PeerRecordEntity;
import com.ghostchu.peerbanhelper.database.table.TorrentEntity;
import com.ghostchu.peerbanhelper.util.paging.Page;
import com.ghostchu.peerbanhelper.util.paging.Pageable;
import com.ghostchu.peerbanhelper.wrapper.PeerWrapper;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
import com.j256.ormlite.stmt.SelectArg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Deque;

@Component
@Slf4j
public class PeerRecordDao extends AbstractPBHDao<PeerRecordEntity, Long> {
    private final TorrentDao torrentDao;

    public PeerRecordDao(@Autowired Database database, TorrentDao torrentDao) throws SQLException {
        super(database.getDataSource(), PeerRecordEntity.class);
        this.torrentDao = torrentDao;
    }

    public void syncPendingTasks(Deque<BatchHandleTasks> tasks) throws SQLException {
        callBatchTasks(() -> {
            while (!tasks.isEmpty()) {
                var t = tasks.pop();
                try {
                    writeToDatabase(t.timestamp, t.downloader, t.torrent, t.peer);
                } catch (SQLException e) {
                    log.error("Unable save peer record to database, please report to developer: {}, {}, {}, {}", t.timestamp, t.downloader, t.torrent, t.peer);
                }
            }
            return null;
        });
    }

    public Page<PeerRecordEntity> getPendingSubmitPeerRecords(Pageable pageable, Timestamp afterThan) throws SQLException {
        var queryBuilder = queryBuilder().where()
                .gt("lastTimeSeen", afterThan)
                .or()
                .isNull("lastTimeSeen")
                .queryBuilder()
                .orderBy("lastTimeSeen", false);
        return queryByPaging(queryBuilder, pageable);
    }

    private int writeToDatabase(long timestamp, String downloader, TorrentWrapper torrent, PeerWrapper peer) throws SQLException {
        TorrentEntity torrentEntity = torrentDao.createIfNotExists(new TorrentEntity(
                null,
                torrent.getHash(),
                torrent.getName(),
                torrent.getSize()
        ));
        PeerRecordEntity currentSnapshot = new PeerRecordEntity(
                null,
                peer.toPeerAddress().getAddress().toNormalizedString(),
                torrentEntity,
                downloader,
                peer.getId().length() > 8 ? peer.getId().substring(0, 8) : peer.getId(),
                peer.getClientName(),
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
        databaseSnapshot.setPeerId(currentSnapshot.getPeerId());
        databaseSnapshot.setClientName(currentSnapshot.getClientName());
        databaseSnapshot.setLastFlags(currentSnapshot.getLastFlags());
        databaseSnapshot.setLastTimeSeen(currentSnapshot.getLastTimeSeen());
        return update(databaseSnapshot);
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
