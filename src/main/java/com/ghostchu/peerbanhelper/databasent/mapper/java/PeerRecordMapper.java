package com.ghostchu.peerbanhelper.databasent.mapper.java;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.peerbanhelper.databasent.dto.IPAddressTimeSeen;
import com.ghostchu.peerbanhelper.databasent.dto.IPAddressTotalTraffic;
import com.ghostchu.peerbanhelper.databasent.table.PeerRecordEntity;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.time.OffsetDateTime;

public interface PeerRecordMapper extends BaseMapper<PeerRecordEntity> {
    long sessionBetween(@NotNull String downloader, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt);

    IPAddressTotalTraffic queryAddressTotalTraffic(InetAddress address);

    IPAddressTimeSeen queryAddressTimeSeen(InetAddress address);

    @NotNull Page<PeerRecordEntity> queryAccessHistoryByIp(@NotNull Page<PeerRecordEntity> page, @NotNull InetAddress ip, @NotNull String orderBySql);
}
