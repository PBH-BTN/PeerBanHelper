package com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
com.biglybt.pif.peers.PeerStats.java
 */
@AllArgsConstructor
@Data
@NoArgsConstructor
public final class PeerStatsRecord {
    private long rtDownloadSpeed;
    private long reception;
    private long rtUploadSpeed;
    private long totalSpeed;
    private long totalDiscarded;
    private long totalSent;
    private long totalReceived;
    private long statisticSentSpeed;
    private long permittedBytesToReceive;
    private long permittedBytesToSend;
    private long overallBytesRemaining;
}
