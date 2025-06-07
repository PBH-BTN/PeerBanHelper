package com.ghostchu.peerbanhelper.metric.impl.inmemory;

import com.ghostchu.peerbanhelper.metric.BasicMetrics;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import org.springframework.stereotype.Component;

// 简易记录，后续看情况添加 SQLite 数据库记录更详细的信息
@Component("inMemoryMetrics")
public final class InMemoryMetrics implements BasicMetrics {
    private long checks = 0;
    private long bans = 0;
    private long unbans = 0;

    @Override
    public long getCheckCounter() {
        return checks;
    }

    @Override
    public long getPeerBanCounter() {
        return bans;
    }

    @Override
    public long getPeerUnbanCounter() {
        return unbans;
    }

    @Override
    public void recordCheck() {
        checks++;
    }

    @Override
    public void recordPeerBan(PeerAddress address, BanMetadata metadata) {
        if(metadata.isBanForDisconnect()){
            return;
        }
        bans++;
    }

    @Override
    public void recordPeerUnban(PeerAddress address, BanMetadata metadata) {
        if(metadata.isBanForDisconnect()){
            return;
        }
        unbans++;
    }

    @Override
    public void flush() {
        // do nothing for in-memory
    }

    @Override
    public void close() {
        // do nothing for in-memory
    }
}
