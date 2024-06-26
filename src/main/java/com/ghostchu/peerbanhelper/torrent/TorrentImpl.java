package com.ghostchu.peerbanhelper.torrent;

import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@Setter
public class TorrentImpl implements Torrent {
    private double progress;
    private long rtUploadSpeed;
    private long rtDownloadSpeed;
    @NotNull
    private String hash;
    @NotNull
    private String id;
    @NotNull
    private String name;
    private long size;

    public TorrentImpl(@NotNull String id, @NotNull String name, @NotNull String hash,
                       long size, double progress, long rtUploadSpeed,
                       long rtDownloadSpeed) {
        this.id = id;
        this.name = name;
        this.hash = hash;
        this.size = size;
        this.progress = progress;
        this.rtUploadSpeed = rtUploadSpeed;
        this.rtDownloadSpeed = rtDownloadSpeed;
    }

    @Override
    public @NotNull String getId() {
        return id;
    }

    @Override
    public @NotNull String getName() {
        return name;
    }

    @Override
    public @NotNull String getHash() {
        return hash;
    }

    @Override
    public double getProgress() {
        return progress;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public long getRtUploadSpeed() {
        return rtUploadSpeed;
    }

    @Override
    public long getRtDownloadSpeed() {
        return rtDownloadSpeed;
    }
}
