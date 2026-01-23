package com.ghostchu.peerbanhelper.databasent.mapper.java;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.peerbanhelper.databasent.table.PCBAddressEntity;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface PCBAddressMapper extends BaseMapper<PCBAddressEntity> {
	List<PCBAddressEntity> fetchFromDatabase(@NotNull @Param("torrentId") String torrentId, @NotNull @Param("downloader") String downloader);

	PCBAddressEntity fetchFromDatabaseOne(@NotNull String torrentId, @NotNull String ip, int port, @NotNull String downloader);
}
