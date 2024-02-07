package com.ghostchu.peerbanhelper.torrent;

import lombok.Setter;

@Setter
public class TorrentImpl implements Torrent {
    private String id;
    private String name;
    private long size;
    private long downloaded;

    public TorrentImpl(String id, String name, long size, long downloaded) {
        this.id = id;
        this.name = name;
        this.size = size;
        this.downloaded = downloaded;
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
    public long getSize() {
        return size;
    }

    @Override
    public long getDownloaded() {
        return downloaded;
    }

}
