package com.ghostchu.peerbanhelper.torrent;

import lombok.Setter;

public class TorrentImpl implements Torrent {
    private final double progress;
    @Setter
    private String hash;
    @Setter
    private String id;
    @Setter
    private String name;
    @Setter
    private long size;

    public TorrentImpl(String id, String name, String hash, long size,double progress) {
        this.id = id;
        this.name = name;
        this.hash = hash;
        this.size = size;
        this.progress = progress;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getHash() {
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


}
