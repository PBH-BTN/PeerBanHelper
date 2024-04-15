package com.ghostchu.peerbanhelper.torrent;

public class TorrentImpl implements Torrent {
    private final double progress;
    private String hash;
    private String id;
    private String name;
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


    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
}
