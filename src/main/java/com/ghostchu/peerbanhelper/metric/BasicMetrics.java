package com.ghostchu.peerbanhelper.metric;

import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import inet.ipaddr.IPAddress;
import org.jetbrains.annotations.NotNull;

public interface BasicMetrics {
    long getCheckCounter();

    long getPeerBanCounter();

    long getPeerUnbanCounter();

    long getSavedTraffic();

    long getWastedTraffic();

    void recordCheck();

    void recordPeerBan(@NotNull IPAddress address, @NotNull BanMetadata metadata);

    void recordPeerUnban(@NotNull IPAddress address, @NotNull BanMetadata metadata);

    void flush();

    void close();
}
