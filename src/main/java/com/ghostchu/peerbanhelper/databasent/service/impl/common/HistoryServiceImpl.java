package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.peerbanhelper.databasent.dto.PeerBanCount;
import com.ghostchu.peerbanhelper.databasent.dto.UniversalFieldNumResult;
import com.ghostchu.peerbanhelper.databasent.mapper.java.HistoryMapper;
import com.ghostchu.peerbanhelper.databasent.service.HistoryService;
import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;
import com.ghostchu.peerbanhelper.util.query.Orderable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class HistoryServiceImpl extends ServiceImpl<HistoryMapper, HistoryEntity> implements HistoryService {

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
        return baseMapper.selectCount(new QueryWrapper<HistoryEntity>().eq("torrent_id", id));
    }

    @Override
    public long countHistoriesByIp(@NotNull InetAddress inetAddress) {
        return baseMapper.selectCount(new QueryWrapper<HistoryEntity>().eq("ip", inetAddress));
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
    public int deleteExpiredLogs(int keepDays) {
        return baseMapper.delete(new QueryWrapper<HistoryEntity>().le("ban_at", OffsetDateTime.now().minusDays(keepDays)));
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
        return switch (field) {
            case "torrent_name" -> "t.name";
            case "module_name" -> "h.module_name";
            case "peer_client_name" -> "h.peer_client_name";
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
        return baseMapper.selectPage(pageRequest, orderable.apply(null));
    }
}
