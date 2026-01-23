package com.ghostchu.peerbanhelper.databasent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.peerbanhelper.databasent.dto.IPAddressTimeSeen;
import com.ghostchu.peerbanhelper.databasent.dto.IPAddressTotalTraffic;
import com.ghostchu.peerbanhelper.databasent.table.PeerRecordEntity;
import com.ghostchu.peerbanhelper.util.query.Orderable;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;

public interface PeerRecordService extends IService<PeerRecordEntity> {

    long countRecordsByIp(@NotNull InetAddress inetAddress);

    IPAddressTotalTraffic queryAddressTotalTraffic(@NotNull InetAddress inet);

    IPAddressTimeSeen queryAddressTimeSeen(@NotNull InetAddress inet);

    @NotNull IPage<PeerRecordEntity> queryAccessHistoryByIp(@NotNull Page<PeerRecordEntity> page, @NotNull InetAddress ip, @NotNull Orderable orderable);
}
