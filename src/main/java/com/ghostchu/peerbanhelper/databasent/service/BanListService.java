package com.ghostchu.peerbanhelper.databasent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.peerbanhelper.BanList;
import com.ghostchu.peerbanhelper.databasent.table.BanListEntity;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import inet.ipaddr.IPAddress;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Map;

public interface BanListService extends IService<BanListEntity> {
    @NotNull Map<IPAddress, BanMetadata> readBanList();

    int saveBanList(@NotNull BanList banlist);
}
