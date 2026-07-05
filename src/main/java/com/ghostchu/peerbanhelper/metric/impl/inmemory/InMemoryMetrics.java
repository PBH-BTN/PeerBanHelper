package com.ghostchu.peerbanhelper.metric.impl.inmemory;

import com.ghostchu.peerbanhelper.downloader.DownloaderBasicInfo;
import com.ghostchu.peerbanhelper.metric.BasicMetrics;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.wrapper.*;
import inet.ipaddr.IPAddress;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;

// 简易记录，后续看情况添加 SQLite 数据库记录更详细的信息
@Component("inMemoryMetrics")
public class InMemoryMetrics implements BasicMetrics {
    protected final LongAdder checks = new LongAdder();
    protected final LongAdder bans = new LongAdder();
    protected final LongAdder unbans = new LongAdder();
    protected final LongAdder wastedTraffic = new LongAdder();
    protected final LongAdder savedTraffic = new LongAdder();

    @Override
    public long getCheckCounter() {
        return checks.sum();
    }

    @Override
    public long getPeerBanCounter() {
        return bans.sum();
    }

    @Override
    public long getPeerUnbanCounter() {
        return unbans.sum();
    }

    @Override
    public long getSavedTraffic() {
        return savedTraffic.sum();
    }

    @Override
    public long getWastedTraffic() {
        return wastedTraffic.sum();
    }

    @Override
    public void recordCheck() {
        checks.add(1);
    }

    @Override
    public BanMetadata recordPeerBan(@NotNull IPAddress address, DownloaderBasicInfo downloader, OffsetDateTime banAt, OffsetDateTime unbanAt,
                                    boolean excludeFromPersist,  boolean excludeFromNotify, boolean excludeFromReport, boolean excludeFromDisplay, TorrentWrapper torrent, PeerWrapper peer,
                                     BanDetailData banDetailData) {
        BanMetadata banMetadata = new BanMetadata(banDetailData.context(), UUID.randomUUID().toString().replace("-", ""),
                downloader, banAt, unbanAt, excludeFromPersist, excludeFromNotify, excludeFromReport, excludeFromDisplay,torrent, peer, -1);
        if (excludeFromNotify) {
            return banMetadata;
        }
        bans.add(1);
        savedTraffic.add(Math.max(0, torrent.getSize() - peer.getUploaded()));
        wastedTraffic.add(peer.getUploaded());
        return banMetadata;
    }


    @Override
    public synchronized void recordPeerUnban(@NotNull IPAddress address, @NotNull BanMetadata metadata) {
        if (metadata.isExcludeFromNotify()) {
            return;
        }
        unbans.add(1);
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
