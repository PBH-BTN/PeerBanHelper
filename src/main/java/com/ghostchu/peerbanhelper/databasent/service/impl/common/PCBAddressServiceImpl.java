package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.peerbanhelper.databasent.mapper.java.PCBAddressMapper;
import com.ghostchu.peerbanhelper.databasent.service.PCBAddressService;
import com.ghostchu.peerbanhelper.databasent.table.PCBAddressEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class PCBAddressServiceImpl extends ServiceImpl<PCBAddressMapper, PCBAddressEntity> implements PCBAddressService {

	@Override
	public List<PCBAddressEntity> fetchFromDatabase(@NotNull String torrentId, @NotNull String downloader) {
        return baseMapper.selectList(new LambdaQueryWrapper<PCBAddressEntity>()
                .eq(PCBAddressEntity::getTorrentId, torrentId)
                .eq(PCBAddressEntity::getDownloader, downloader));
	}

	@Override
    public PCBAddressEntity fetchFromDatabase(@NotNull String torrentId, @NotNull InetAddress ip, int port, @NotNull String downloader) {
        return baseMapper.selectOne(new LambdaQueryWrapper<PCBAddressEntity>()
                .eq(PCBAddressEntity::getTorrentId, torrentId)
                .eq(PCBAddressEntity::getIp, ip)
                .eq(PCBAddressEntity::getPort, port)
                .eq(PCBAddressEntity::getDownloader, downloader));
	}

	@Override
    public int deleteEntry(@NotNull String torrentId, @NotNull InetAddress ip) {
        return baseMapper.delete(new LambdaQueryWrapper<PCBAddressEntity>()
                .eq(PCBAddressEntity::getTorrentId, torrentId)
                .eq(PCBAddressEntity::getIp, ip));
	}

	@Override
    public int cleanupDatabase(OffsetDateTime timestamp) {
        return baseMapper.delete(new LambdaQueryWrapper<PCBAddressEntity>()
                .lt(PCBAddressEntity::getLastTimeSeen, timestamp));
	}
}
