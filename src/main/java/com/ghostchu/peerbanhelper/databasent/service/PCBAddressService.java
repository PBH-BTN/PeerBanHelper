package com.ghostchu.peerbanhelper.databasent.service;

import com.ghostchu.peerbanhelper.databasent.table.PCBAddressEntity;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;

public interface PCBAddressService extends CommonCanDirtyService<PCBAddressEntity> {
    List<PCBAddressEntity> fetchFromDatabase(@NotNull String torrentId, @NotNull String downloader);

    PCBAddressEntity fetchFromDatabase(@NotNull String torrentId, @NotNull InetAddress ip, int port, @NotNull String downloader);

    int deleteEntry(@NotNull String torrentId, @NotNull InetAddress ip);

    long cleanupDatabase(OffsetDateTime timestamp);
}
