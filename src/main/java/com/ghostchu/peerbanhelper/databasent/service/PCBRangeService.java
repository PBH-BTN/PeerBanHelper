package com.ghostchu.peerbanhelper.databasent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.peerbanhelper.databasent.table.PCBRangeEntity;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

public interface PCBRangeService extends IService<PCBRangeEntity> {

	List<PCBRangeEntity> fetchFromDatabase(@NotNull String torrentId, @NotNull String downloader);

	PCBRangeEntity fetchFromDatabase(@NotNull String torrentId, @NotNull String range, @NotNull String downloader);

	int deleteEntry(@NotNull String torrentId,  @NotNull String range);

    long cleanupDatabase(OffsetDateTime timestamp);
}
