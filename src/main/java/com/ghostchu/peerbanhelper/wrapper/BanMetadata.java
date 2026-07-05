package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.downloader.DownloaderBasicInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;


@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Data
public class BanMetadata extends PeerMetadata implements Serializable {
    private String context;
    private String randomId;
    private OffsetDateTime banAt;
    private OffsetDateTime unbanAt;
    private boolean excludeFromPersist;
    private boolean excludeFromNotify;
    private boolean excludeFromReport;
    private boolean excludeFromDisplay;
    private long linkedHistoryId = -1; // persist
    private transient String reserveDnsLookup = "N/A";

    public BanMetadata(String context, String randomId, DownloaderBasicInfo downloader, OffsetDateTime banAt, OffsetDateTime unbanAt,
                       boolean excludeFromPersist, boolean excludeFromNotify, boolean excludeFromReport, boolean excludeFromDisplay,
                       TorrentWrapper torrent, PeerWrapper peer, long linkedHistoryId) {
        super(downloader, torrent, peer);
        this.context = context;
        this.randomId = randomId;
        this.banAt = banAt;
        this.unbanAt = unbanAt;
        this.excludeFromPersist = excludeFromPersist;
        this.excludeFromNotify = excludeFromNotify;
        this.excludeFromDisplay = excludeFromDisplay;
        this.excludeFromReport = excludeFromReport;
        this.linkedHistoryId = linkedHistoryId;
    }

    @Override
    public String toString() {
        return "BanMetadata{" +
                "context='" + context + '\'' +
                ", randomId='" + randomId + '\'' +
                ", banAt=" + banAt +
                ", unbanAt=" + unbanAt +
                ", excludeFromPersist=" + excludeFromPersist +
                ", excludeFromNotify=" + excludeFromNotify +
                ", excludeFromReport=" + excludeFromReport +
                ", excludeFromDisplay=" + excludeFromDisplay +
                ", linkedHistoryId=" + linkedHistoryId +
                ", reserveDnsLookup='" + reserveDnsLookup + '\'' +
                '}';
    }
}
