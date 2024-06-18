package com.ghostchu.peerbanhelper.metric;

import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;

public interface BasicMetrics {
    long getCheckCounter();

    long getPeerBanCounter();

    long getPeerUnbanCounter();

    void recordCheck();

    void recordPeerBan(PeerAddress address, BanMetadata metadata);

    void recordPeerUnban(PeerAddress address, BanMetadata metadata);

    void flush();

    void close();
}
