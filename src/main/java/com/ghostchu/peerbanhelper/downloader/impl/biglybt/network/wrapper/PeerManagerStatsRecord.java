package com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Data
@NoArgsConstructor
public class PeerManagerStatsRecord {
    private int connectedSeeds;
    private int connectedLeechers;
    private long downloaded;
    private long uploaded;
    private long rtDownloadSpeed;
    private long rtUploadSpeed;
    private long discarded;
    private long hashFailBytes;
    private long permittedBytesToReceived;
    private long permittedBytesToSend;
}
