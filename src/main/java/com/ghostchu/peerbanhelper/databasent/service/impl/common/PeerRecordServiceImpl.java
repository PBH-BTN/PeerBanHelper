package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.peerbanhelper.databasent.dto.ClientAnalyseResult;
import com.ghostchu.peerbanhelper.databasent.dto.IPAddressTimeSeen;
import com.ghostchu.peerbanhelper.databasent.dto.IPAddressTotalTraffic;
import com.ghostchu.peerbanhelper.databasent.dto.TorrentCount;
import com.ghostchu.peerbanhelper.databasent.mapper.java.PeerRecordMapper;
import com.ghostchu.peerbanhelper.databasent.routing.WriteDataSource;
import com.ghostchu.peerbanhelper.databasent.service.PeerRecordService;
import com.ghostchu.peerbanhelper.databasent.service.TorrentService;
import com.ghostchu.peerbanhelper.databasent.table.PeerRecordEntity;
import com.ghostchu.peerbanhelper.databasent.table.TorrentEntity;
import com.ghostchu.peerbanhelper.util.ipdb.IPDBManager;
import com.ghostchu.peerbanhelper.util.query.Orderable;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.ghostchu.peerbanhelper.wrapper.PeerWrapper;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ghostchu.peerbanhelper.util.TimeUtil.zeroOffsetDateTime;

@Slf4j
@Service
public class PeerRecordServiceImpl extends ServiceImpl<PeerRecordMapper, PeerRecordEntity> implements PeerRecordService {
    @Autowired
    private TorrentService torrentDao;
    @Autowired
    private IPDBManager ipdbManager;

    @Override
    public List<PeerRecordEntity> getRecordsBetween(OffsetDateTime start, OffsetDateTime end, String downloader) {
        return baseMapper.selectList(new LambdaQueryWrapper<PeerRecordEntity>()
                .ge(PeerRecordEntity::getFirstTimeSeen, start)
                .le(PeerRecordEntity::getLastTimeSeen, end)
                .eq(downloader != null, PeerRecordEntity::getDownloader, downloader));
    }

    @Override
    @WriteDataSource
    public synchronized void flushToDatabase(BatchHandleTasks t) {
        var torrent = t.torrent;
        var peer = t.peer;
        var downloader = t.downloader;
        var timestamp = t.timestamp;
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
        PeerRecordEntity databaseSnapshot = baseMapper.selectOne(new LambdaQueryWrapper<PeerRecordEntity>()
                .eq(PeerRecordEntity::getAddress, currentSnapshot.getAddress())
                .eq(PeerRecordEntity::getPort, currentSnapshot.getPort())
                .eq(PeerRecordEntity::getTorrentId, currentSnapshot.getTorrentId())
                .eq(PeerRecordEntity::getDownloader, currentSnapshot.getDownloader())
        );
        if (databaseSnapshot == null) {
            databaseSnapshot = currentSnapshot;
        }
        if (databaseSnapshot.getLastTimeSeen().isAfter(timestamp)) {
            return;
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
        baseMapper.insertOrUpdate(databaseSnapshot);
    }

    @Override
    public long sessionBetween(@NotNull String downloader, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt) {
        // 从 startAt 到 endAt，每天的开始时间戳
        return baseMapper.sessionBetween(downloader, startAt, endAt);
    }


    @Override
    public Page<PeerRecordEntity> getPendingSubmitPeerRecords(@NotNull Pageable pageable, @NotNull OffsetDateTime afterThan) {
        return baseMapper.selectPage(pageable.toPage(),
                new LambdaQueryWrapper<PeerRecordEntity>().gt(PeerRecordEntity::getLastTimeSeen, afterThan)
                        .or()
                        .isNull(PeerRecordEntity::getLastTimeSeen)
                        .orderByAsc(PeerRecordEntity::getLastTimeSeen)
        );
    }

    @Override
    public long countRecordsByIp(@NotNull InetAddress inetAddress) {
        return baseMapper.selectCount(new LambdaQueryWrapper<PeerRecordEntity>().eq(PeerRecordEntity::getAddress, inetAddress));
    }

    @Override
    public IPAddressTotalTraffic queryAddressTotalTraffic(@NotNull InetAddress inet) {
        IPAddressTotalTraffic traffic = baseMapper.queryAddressTotalTraffic(inet);
        if (traffic == null) {
            traffic = new IPAddressTotalTraffic();
            traffic.setTotalUploaded(-1);
            traffic.setTotalDownloaded(-1);
        }
        return traffic;
    }

    @Override
    public IPAddressTimeSeen queryAddressTimeSeen(@NotNull InetAddress inet) {
        IPAddressTimeSeen timeSeen = baseMapper.queryAddressTimeSeen(inet);
        if (timeSeen == null) {
            timeSeen = new IPAddressTimeSeen();
            timeSeen.setFirstTimeSeen(zeroOffsetDateTime);
            timeSeen.setLastTimeSeen(zeroOffsetDateTime);
        }
        return timeSeen;
    }

    @Override
    public @NotNull Page<PeerRecordEntity> queryAccessHistoryByIp(@NotNull Page<PeerRecordEntity> page, @NotNull InetAddress ip, @NotNull Orderable orderable) {
        return baseMapper.queryAccessHistoryByIp(page, ip, orderable.generateOrderBy());
    }

    @Override
    public @NotNull Page<ClientAnalyseResult> queryClientAnalyse(@NotNull Page<ClientAnalyseResult> page, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt, @Nullable String downloader, @NotNull String orderBySql) {
        return baseMapper.queryClientAnalyse(page, startAt, endAt, downloader, orderBySql);
    }

    @Override
    public long countRecordsByTorrentId(Long id) {
        return baseMapper.selectCount(new LambdaQueryWrapper<PeerRecordEntity>().eq(PeerRecordEntity::getTorrentId, id));
    }

    @Override
    public @NotNull Page<PeerRecordEntity> queryAccessHistoryByTorrentId(@NotNull Page<PeerRecordEntity> page, @NotNull Long id, @NotNull Orderable orderable) {
        return baseMapper.selectPage(page, orderable.apply(new QueryWrapper<PeerRecordEntity>().eq("torrent_id", id)));
    }

    @Override
    public Map<Long, Long> countByTorrentIds(@NotNull List<Long> torrentIds) {
        if (torrentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<TorrentCount> counts = baseMapper.countByTorrentIds(torrentIds);
        return counts.stream().collect(Collectors.toMap(TorrentCount::getTorrentId, TorrentCount::getCount));
    }

    @Override
    public List<String> getDistinctIps(@NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt, @Nullable String downloader) {
        return baseMapper.getDistinctIps(startAt, endAt, downloader);
    }


    public record BatchHandleTasks(OffsetDateTime timestamp, String downloader, TorrentWrapper torrent,
                                   PeerWrapper peer) {

    }
}
