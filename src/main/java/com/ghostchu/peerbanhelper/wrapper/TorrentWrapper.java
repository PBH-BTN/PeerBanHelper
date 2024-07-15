package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.torrent.Torrent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public final class TorrentWrapper {
    private String id;
    private long size;
    private String name;
    private String hash;
    private double progress;
    private long rtUploadSpeed;
    private long rtDownloadSpeed;

    public TorrentWrapper(Torrent torrent) {
        this.id = torrent.getId();
        this.size = torrent.getSize();
        this.name = torrent.getName();
        this.hash = torrent.getHash();
        this.progress = torrent.getProgress();
        this.rtDownloadSpeed = torrent.getRtDownloadSpeed();
        this.rtUploadSpeed = torrent.getRtUploadSpeed();
    }
}
