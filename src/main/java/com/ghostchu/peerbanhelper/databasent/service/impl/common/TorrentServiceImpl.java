package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.peerbanhelper.databasent.mapper.java.TorrentMapper;
import com.ghostchu.peerbanhelper.databasent.service.TorrentService;
import com.ghostchu.peerbanhelper.databasent.table.TorrentEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class TorrentServiceImpl extends ServiceImpl<TorrentMapper, TorrentEntity> implements TorrentService {
    @Override
    public @NotNull TorrentEntity createIfNotExists(@NotNull TorrentEntity torrent) {
        return baseMapper.createIfNotExists(torrent);
    }

    @Override
    public @Nullable TorrentEntity queryByInfoHash(@NotNull String infoHash) {
        return baseMapper.selectOne(new QueryWrapper<TorrentEntity>().eq("info_hash", infoHash));
    }
}
