package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.peerbanhelper.databasent.dto.PeerBanCount;
import com.ghostchu.peerbanhelper.databasent.dto.TorrentCount;
import com.ghostchu.peerbanhelper.databasent.dto.UniversalFieldNumResult;
import com.ghostchu.peerbanhelper.databasent.mapper.java.HistoryMapper;
import com.ghostchu.peerbanhelper.databasent.service.HistoryService;
import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;
import com.ghostchu.peerbanhelper.util.query.Orderable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class HistoryServiceImpl extends AbstractCommonService<HistoryMapper, HistoryEntity> implements HistoryService {

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Override
    public IPage<PeerBanCount> getBannedIps(@NotNull Page<PeerBanCount> page, @Nullable String filter) {
        if (filter != null && !filter.isEmpty()) {
            return baseMapper.getBannedIpsWithFilter(page, filter);
        } else {
            return baseMapper.getBannedIpsWithoutFilter(page);
        }
    }

    @Override
    public long countHistoriesByTorrentId(@NotNull Long id) {
        return baseMapper.selectCount(new LambdaQueryWrapper<HistoryEntity>().eq(HistoryEntity::getTorrentId, id));
    }

    @Override
    public long countHistoriesByIp(@NotNull InetAddress inetAddress) {
        return baseMapper.selectCount(new LambdaQueryWrapper<HistoryEntity>().eq(HistoryEntity::getIp, inetAddress));
    }

    @Override
    public IPage<HistoryEntity> queryBanHistoryByIp(@NotNull Page<HistoryEntity> pageable, @NotNull InetAddress ip,
                                                    @NotNull Orderable orderBy) {
        return baseMapper.selectPage(pageable, orderBy.apply(new QueryWrapper<HistoryEntity>().eq("ip", ip)));
    }

    @Override
    public IPage<HistoryEntity> queryBanHistoryByTorrentId(@NotNull Page<HistoryEntity> pageable, @NotNull Long torrentId, @NotNull Orderable orderBy) {
        return baseMapper.selectPage(pageable, orderBy.apply(new QueryWrapper<HistoryEntity>().eq("torrent_id", torrentId)));
    }

    @Override
    public long deleteExpiredLogs(int keepDays) {
        OffsetDateTime thresholdDate = OffsetDateTime.now().minusDays(keepDays);
        return splitBatchDelete(new LambdaQueryWrapper<HistoryEntity>().select(HistoryEntity::getId).le(HistoryEntity::getBanAt, thresholdDate));
    }

    @Override
    public List<UniversalFieldNumResult> countField(@NotNull String field, double percentFilter,
                                                    @Nullable String downloader, @Nullable Integer substringLength) {
        String mappedField = mapField(field);
        return baseMapper.countField(mappedField, percentFilter, downloader, substringLength);
    }

    @Override
    public List<UniversalFieldNumResult> sumField(@NotNull String field, double percentFilter,
                                                  @Nullable String downloader, @Nullable Integer substringLength) {
        String mappedField = mapField(field);
        return baseMapper.sumField(mappedField, percentFilter, downloader, substringLength);
    }

    private String mapField(String field) {
        //驼峰转下划线
        field = field.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
        return switch (field) {
            case "torrent_name" -> "t.name";
            case "module" -> "module_name";
            case "module_name" -> "h.module_name";
            case "client_name", "peer_client_name" -> "h.peer_client_name";
            case "peer_ip", "ip" -> "h.ip";
            case "port" -> "h.port";
            case "peer_id" -> "h.peer_id";
            case "peer_uploaded" -> "h.peer_uploaded";
            case "peer_downloaded" -> "h.peer_downloaded";
            case "peer_progress" -> "h.peer_progress";
            case "time" -> "h.ban_at";
            default -> field; // Fallback to original, assuming it's a valid column or expression
        };
    }

    @Override
    public IPage<HistoryEntity> getBanLogs(Page<HistoryEntity> pageRequest, Orderable orderable) {
        return baseMapper.selectPage(pageRequest, orderable.apply(new QueryWrapper<>()));
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
    public List<String> getDistinctIps(@NotNull OffsetDateTime start, @NotNull OffsetDateTime end, @Nullable String downloader) {
        return baseMapper.getDistinctIps(start, end, downloader);
    }
}
