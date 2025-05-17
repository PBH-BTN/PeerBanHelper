package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public final class QBittorrentTorrentTrackers {
    @JsonProperty("msg")
    private String msg;
    @JsonProperty("num_downloaded")
    private Integer numDownloaded;
    @JsonProperty("num_leeches")
    private Integer numLeeches;
    @JsonProperty("num_peers")
    private Integer numPeers;
    @JsonProperty("num_seeds")
    private Integer numSeeds;
    @JsonProperty("status")
    private Integer status;
    @JsonProperty("tier")
    private Integer tier;
    @JsonProperty("url")
    private String url;
}
