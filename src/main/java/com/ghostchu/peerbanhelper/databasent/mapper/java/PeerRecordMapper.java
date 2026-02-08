package com.ghostchu.peerbanhelper.databasent.mapper.java;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.peerbanhelper.databasent.dto.ClientAnalyseResult;
import com.ghostchu.peerbanhelper.databasent.dto.IPAddressTimeSeen;
import com.ghostchu.peerbanhelper.databasent.dto.IPAddressTotalTraffic;
import com.ghostchu.peerbanhelper.databasent.dto.TorrentCount;
import com.ghostchu.peerbanhelper.databasent.table.PeerRecordEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;

public interface PeerRecordMapper extends BaseMapper<PeerRecordEntity> {
    @Select("SELECT COUNT(DISTINCT address) FROM peer_records WHERE downloader = #{downloader} AND lastTimeSeen >= #{startAt} AND firstTimeSeen <= #{endAt}")
    long sessionBetween(@NotNull String downloader, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt);

    IPAddressTotalTraffic queryAddressTotalTraffic(InetAddress address);

    IPAddressTimeSeen queryAddressTimeSeen(InetAddress address);

    @NotNull Page<PeerRecordEntity> queryAccessHistoryByIp(@NotNull Page<PeerRecordEntity> page, @NotNull InetAddress ip, @NotNull String orderBySql);

    @NotNull Page<ClientAnalyseResult> queryClientAnalyse(@NotNull Page<ClientAnalyseResult> page, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt, @Nullable String downloader, @NotNull String orderBySql);

    List<TorrentCount> countByTorrentIds(@Param("torrentIds") List<Long> torrentIds);

    List<String> getDistinctIps(@Param("start") java.time.OffsetDateTime start,
                                @Param("end") java.time.OffsetDateTime end,
                                @Param("downloader") String downloader);
}
