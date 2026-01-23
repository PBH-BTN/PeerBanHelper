package com.ghostchu.peerbanhelper.databasent.mapper.java;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.peerbanhelper.databasent.table.PeerRecordEntity;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

public interface PeerRecordMapper extends BaseMapper<PeerRecordEntity> {
    long sessionBetween(@NotNull String downloader, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt);
}
