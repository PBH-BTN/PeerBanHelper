package com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/*
com.biglybt.pif.download.Download.java
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public final class DownloadRecord {
    private int state;
    private int subState;
    private long flags;
    private TorrentRecord torrent;
    private boolean forceStart;
    private boolean paused;
    private String name;
    private String categoryName;
    private List<String> tags;
    private int position;
    private long creationTime;
    private DownloadStatsRecord stats;
    private boolean complete;
    private boolean checking;
    private boolean moving;
    private String downloadPeerIdISO88591;
    private boolean removed;
    private List<List<String>> trackers;
}
