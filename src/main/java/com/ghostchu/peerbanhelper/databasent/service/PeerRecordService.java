package com.ghostchu.peerbanhelper.databasent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.peerbanhelper.databasent.table.PeerRecordEntity;

import java.net.InetAddress;

public interface PeerRecordService extends IService<PeerRecordEntity> {

    long countRecordsByIp(InetAddress inetAddress);
}
