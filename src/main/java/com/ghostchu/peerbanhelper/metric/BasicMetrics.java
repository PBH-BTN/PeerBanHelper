package com.ghostchu.peerbanhelper.metric;

import com.ghostchu.peerbanhelper.downloader.DownloaderBasicInfo;
import com.ghostchu.peerbanhelper.wrapper.*;
import inet.ipaddr.IPAddress;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

public interface BasicMetrics {
    long getCheckCounter();

    long getPeerBanCounter();

    long getPeerUnbanCounter();

    long getSavedTraffic();

    long getWastedTraffic();

    void recordCheck();

    BanMetadata recordPeerBan(@NotNull IPAddress address, DownloaderBasicInfo downloader, OffsetDateTime banAt, OffsetDateTime unbanAt,
                              boolean excludeFromPersist, boolean excludeFromNotify, boolean excludeFromReport, boolean excludeFromDisplay, TorrentWrapper torrent, PeerWrapper peer,
                              BanDetailData banDetailData);

    void recordPeerUnban(@NotNull IPAddress address, @NotNull BanMetadata metadata);

    void flush();

    void close();
}
