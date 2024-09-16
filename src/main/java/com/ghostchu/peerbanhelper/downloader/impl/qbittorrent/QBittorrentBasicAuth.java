package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class QBittorrentBasicAuth {
    private String user;
    private String pass;
}
