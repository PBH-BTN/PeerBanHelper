package com.ghostchu.peerbanhelper.databasent.mapper.java;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.peerbanhelper.databasent.dto.TrafficDataComputed;
import com.ghostchu.peerbanhelper.databasent.table.TrafficJournalEntity;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

public interface TrafficJournalMapper extends BaseMapper<TrafficJournalEntity> {
    List<TrafficDataComputed> selectAllDownloadersOverallData(
            @Param("start") @NotNull OffsetDateTime start,
            @Param("end") @NotNull OffsetDateTime end);

    List<TrafficDataComputed> selectSpecificDownloaderOverallData(
            @Param("downloader") @NotNull String downloader,
            @Param("start") @NotNull OffsetDateTime start,
            @Param("end") @NotNull OffsetDateTime end);
}
