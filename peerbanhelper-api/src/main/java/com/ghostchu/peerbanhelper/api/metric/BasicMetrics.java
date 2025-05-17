package com.ghostchu.peerbanhelper.api.metric;

import com.ghostchu.peerbanhelper.api.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.api.wrapper.PeerAddress;
import org.jetbrains.annotations.NotNull;

public interface BasicMetrics {
    long getCheckCounter();

    long getPeerBanCounter();

    long getPeerUnbanCounter();

    void recordCheck();

    void recordPeerBan(@NotNull PeerAddress address, @NotNull BanMetadata metadata);

    void recordPeerUnban(@NotNull PeerAddress address, @NotNull BanMetadata metadata);

    void flush();

    void close();
}
