package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl;


import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public final class QBittorrentMainData {

    @SerializedName("server_state")
    private ServerStateDTO serverState;

    @NoArgsConstructor
    @Data
    public static class ServerStateDTO {
        @SerializedName("alltime_dl")
        private Long alltimeDl;
        @SerializedName("alltime_ul")
        private Long alltimeUl;
        @SerializedName("average_time_queue")
        private Long averageTimeQueue;
        @SerializedName("connection_status")
        private String connectionStatus;
        @SerializedName("dht_nodes")
        private Long dhtNodes;
        @SerializedName("dl_info_data")
        private Long dlInfoData;
        @SerializedName("dl_info_speed")
        private Long dlInfoSpeed;
        @SerializedName("dl_rate_limit")
        private Long dlRateLimit;
        @SerializedName("free_space_on_disk")
        private Long freeSpaceOnDisk;
        @SerializedName("global_ratio")
        private String globalRatio;
        @SerializedName("queued_io_jobs")
        private Long queuedIoJobs;
        @SerializedName("queueing")
        private Boolean queueing;
        @SerializedName("read_cache_hits")
        private String readCacheHits;
        @SerializedName("read_cache_overload")
        private String readCacheOverload;
        @SerializedName("refresh_interval")
        private Long refreshInterval;
        @SerializedName("total_buffers_size")
        private Long totalBuffersSize;
        @SerializedName("total_peer_connections")
        private Long totalPeerConnections;
        @SerializedName("total_queued_size")
        private Long totalQueuedSize;
        @SerializedName("total_wasted_session")
        private Long totalWastedSession;
        @SerializedName("up_info_data")
        private Long upInfoData;
        @SerializedName("up_info_speed")
        private Long upInfoSpeed;
        @SerializedName("up_rate_limit")
        private Long upRateLimit;
        @SerializedName("use_alt_speed_limits")
        private Boolean useAltSpeedLimits;
        @SerializedName("use_subcategories")
        private Boolean useSubcategories;
        @SerializedName("write_cache_overload")
        private String writeCacheOverload;
    }
}
