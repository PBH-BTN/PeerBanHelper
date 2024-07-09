package com.ghostchu.peerbanhelper.downloader.impl.rtorrent.bean;

import com.ghostchu.peerbanhelper.torrent.Torrent;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public final class RTorrent implements Torrent {
    private final String hash;
    private final boolean open;
    private final boolean hashChecking;
    private final boolean hashChecked;
    private final int state;
    private final String name;
    private final long sizeBytes;
    private final long completedChunks;
    private final long sizeChunks;
    private final long bytesDone;
    private final long upTotal;
    private final double ratio;
    private final long upRate;
    private final long downRate;
    private final long chunkSize;
    private final String custom1;
    private final int peerAccounted;
    private final int peersNotConnected;
    private final int peersConnected;
    private final int peersComplete;
    private final long leftBytes;
    private final int priority;
    private final long stateChanged;
    private final long skipTotal;
    private final boolean hashing;
    private final long chunkHashed;
    private final String basePath;
    private final long creationDate;
    private final int trackerSize;
    private final boolean active;
    private final String message;
    private final String custom2;
    private final long freeDiskSpace;
    private final boolean privateSeed;
    private final boolean multiFile;

    public RTorrent(String[] resp) {
        this.hash = resp[0];
        this.open = Boolean.parseBoolean(resp[1]);
        this.hashChecking = Boolean.parseBoolean(resp[2]);
        this.hashChecked = Boolean.parseBoolean(resp[3]);
        this.state = Integer.parseInt(resp[4]);
        this.name = resp[5];
        this.sizeBytes = Long.parseLong(resp[6]);
        this.completedChunks = Long.parseLong(resp[7]);
        this.sizeChunks = Long.parseLong(resp[8]);
        this.bytesDone = Long.parseLong(resp[9]);
        this.upTotal = Long.parseLong(resp[10]);
        this.ratio = Double.parseDouble(resp[11]);
        this.upRate = Long.parseLong(resp[12]);
        this.downRate = Long.parseLong(resp[13]);
        this.chunkSize = Long.parseLong(resp[14]);
        this.custom1 = resp[15];
        this.peerAccounted = Integer.parseInt(resp[16]);
        this.peersNotConnected = Integer.parseInt(resp[17]);
        this.peersConnected = Integer.parseInt(resp[18]);
        this.peersComplete = Integer.parseInt(resp[19]);
        this.leftBytes = Long.parseLong(resp[20]);
        this.priority = Integer.parseInt(resp[21]);
        this.stateChanged = Long.parseLong(resp[22]);
        this.skipTotal = Long.parseLong(resp[23]);
        this.hashing = Boolean.parseBoolean(resp[24]);
        this.chunkHashed = Long.parseLong(resp[25]);
        this.basePath = resp[26];
        this.creationDate = Long.parseLong(resp[27]);
        this.trackerSize = Integer.parseInt(resp[28]);
        this.active = Boolean.parseBoolean(resp[29]);
        this.message = resp[30];
        this.custom2 = resp[31];
        this.freeDiskSpace = Long.parseLong(resp[32]);
        this.privateSeed = Boolean.parseBoolean(resp[33]);
        this.multiFile = Boolean.parseBoolean(resp[34]);
    }

    @Override
    public String getId() {
        return hash;
    }

    @Override
    public double getProgress() {
        return (double) (sizeBytes - leftBytes) / sizeBytes;
    }

    @Override
    public long getSize() {
        return sizeBytes;
    }

    @Override
    public long getRtUploadSpeed() {
        return upRate;
    }

    @Override
    public long getRtDownloadSpeed() {
        return downRate;
    }

    @Override
    public String getHashedIdentifier() {
        return Torrent.super.getHashedIdentifier();
    }
}
