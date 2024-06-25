package com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class DownloadStatsRecord {
    private String status;
    private int completedInThousandNotation;
    private int checkingDoneInThousandNotation;
    private long downloadedBytes;
    private long downloadedBytesIncludeProtocol;
    private long remainingBytes;
    private long remainingBytesExcludingDND;
    private long uploaded;
    private long uploadedIncludeProtocol;
    private long discarded;
    private long rtDownloadSpeed;
    private long rtDownloadSpeedIncludeProtocol;
    private long rtUploadSpeed;
    private long rtUploadSpeedIncludeProtocol;
    private long rtTotalSpeed;
    private long hashFails;
    private int shareRatioInThousandNotation;
    private long timeStarted;
    private long timeStartedSeeding;
    private float availability;
    private int health;
    private long bytesUnavailable;
}
