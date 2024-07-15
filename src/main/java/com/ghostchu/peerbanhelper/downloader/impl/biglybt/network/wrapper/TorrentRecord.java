package com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
com.biglybt.pif.torrent.Torrent.java
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public final class TorrentRecord {
    private String name;
    private String hashBase64;
    private long size;
    private long creationDate;
    private String createdBy;
    private long pieceSize;
    private long pieceCount;
    private boolean decentralized;
    private boolean privateTorrent;
    private boolean complete;
}
