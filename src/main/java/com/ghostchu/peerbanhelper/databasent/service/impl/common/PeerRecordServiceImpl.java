package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.peerbanhelper.databasent.dto.IPAddressTimeSeen;
import com.ghostchu.peerbanhelper.databasent.dto.IPAddressTotalTraffic;
import com.ghostchu.peerbanhelper.databasent.mapper.java.PeerRecordMapper;
import com.ghostchu.peerbanhelper.databasent.service.PeerRecordService;
import com.ghostchu.peerbanhelper.databasent.service.TorrentService;
import com.ghostchu.peerbanhelper.databasent.table.PeerRecordEntity;
import com.ghostchu.peerbanhelper.databasent.table.TorrentEntity;
import com.ghostchu.peerbanhelper.util.ipdb.IPDBManager;
import com.ghostchu.peerbanhelper.util.query.Orderable;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.ghostchu.peerbanhelper.wrapper.PeerWrapper;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Deque;

@Slf4j
@Service
public class PeerRecordServiceImpl extends ServiceImpl<PeerRecordMapper, PeerRecordEntity> implements PeerRecordService {
    @Autowired
    private TorrentService torrentDao;
    @Autowired
    private IPDBManager ipdbManager;

    @Override
    public void syncPendingTasks(Deque<BatchHandleTasks> tasks) {
        while (!tasks.isEmpty()) {
            var t = tasks.pop();
            try {
                writeToDatabase(t.timestamp, t.downloader, t.torrent, t.peer);
            } catch (SQLException e) {
                log.error("Unable save peer record to database, please report to developer: {}, {}, {}, {}", t.timestamp, t.downloader, t.torrent, t.peer);
                Sentry.captureException(e);
            }
        }
    }

    @Override
    public long sessionBetween(@NotNull String downloader, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt) {
        // 从 startAt 到 endAt，每天的开始时间戳
        return baseMapper.sessionBetween(downloader, startAt, endAt);
    }

    private boolean writeToDatabase(OffsetDateTime timestamp, String downloader, TorrentWrapper torrent, PeerWrapper peer) throws SQLException {
        TorrentEntity torrentEntity = torrentDao.createIfNotExists(new TorrentEntity(
                null,
                torrent.getHash(),
                torrent.getName(),
                torrent.getSize(),
                torrent.isPrivateTorrent()
        ));
        InetAddress inet = peer.getAddress().getAddress().toInetAddress();
        var lazyLoader = ipdbManager.queryIPDB(inet).geoData();
        var peerGeoIp = lazyLoader.get();
        PeerRecordEntity currentSnapshot = new PeerRecordEntity(
                null,
                inet,
                peer.toPeerAddress().getPort(),
                torrentEntity.getId(),
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
                timestamp,
                timestamp,
                peerGeoIp
        );
        PeerRecordEntity databaseSnapshot = createIfNotExists(currentSnapshot);
        if (databaseSnapshot.getLastTimeSeen().isAfter(timestamp)) {
            return false;
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
        return saveOrUpdate(databaseSnapshot);
    }

    @Override
    public Page<PeerRecordEntity> getPendingSubmitPeerRecords(Pageable pageable, OffsetDateTime afterThan) {
        return baseMapper.selectPage(pageable.toPage(),
                new QueryWrapper<PeerRecordEntity>().gt("last_time_seen", afterThan)
                        .or()
                        .isNull("last_time_seen")
                        .orderByAsc("last_time_seen")
        );
    }

    @Override
    public synchronized PeerRecordEntity createIfNotExists(PeerRecordEntity data) throws SQLException {
        PeerRecordEntity existing = baseMapper.selectOne(new QueryWrapper<PeerRecordEntity>()
                .eq("address", data.getAddress())
                .eq("torrent_id", data.getTorrentId())
                .eq("downloader", data.getDownloader())
        );
        if (existing == null) {
            save(data);
            return data;
        } else {
            return existing;
        }
    }

    @Override
    public long countRecordsByIp(@NotNull InetAddress inetAddress) {
        return baseMapper.selectCount(new QueryWrapper<PeerRecordEntity>().eq("address", inetAddress));
    }

    @Override
    public IPAddressTotalTraffic queryAddressTotalTraffic(@NotNull InetAddress inet) {
        return baseMapper.queryAddressTotalTraffic(inet);
    }

    @Override
    public IPAddressTimeSeen queryAddressTimeSeen(@NotNull InetAddress inet) {
        return baseMapper.queryAddressTimeSeen(inet);
    }

    @Override
    public @NotNull Page<PeerRecordEntity> queryAccessHistoryByIp(@NotNull Page<PeerRecordEntity> page, @NotNull InetAddress ip, @NotNull Orderable orderable) {
        return baseMapper.queryAccessHistoryByIp(page, ip, orderable.generateOrderBy());
    }


    public record BatchHandleTasks(OffsetDateTime timestamp, String downloader, TorrentWrapper torrent,
                                   PeerWrapper peer) {

    }
}
