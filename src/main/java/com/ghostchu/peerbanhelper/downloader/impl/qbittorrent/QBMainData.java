package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent;


import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class QBMainData {

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
        private Integer averageTimeQueue;
        @SerializedName("connection_status")
        private String connectionStatus;
        @SerializedName("dht_nodes")
        private Integer dhtNodes;
        @SerializedName("dl_info_data")
        private Integer dlInfoData;
        @SerializedName("dl_info_speed")
        private Integer dlInfoSpeed;
        @SerializedName("dl_rate_limit")
        private Integer dlRateLimit;
        @SerializedName("free_space_on_disk")
        private Long freeSpaceOnDisk;
        @SerializedName("global_ratio")
        private String globalRatio;
        @SerializedName("queued_io_jobs")
        private Integer queuedIoJobs;
        @SerializedName("queueing")
        private Boolean queueing;
        @SerializedName("read_cache_hits")
        private String readCacheHits;
        @SerializedName("read_cache_overload")
        private String readCacheOverload;
        @SerializedName("refresh_interval")
        private Integer refreshInterval;
        @SerializedName("total_buffers_size")
        private Integer totalBuffersSize;
        @SerializedName("total_peer_connections")
        private Integer totalPeerConnections;
        @SerializedName("total_queued_size")
        private Integer totalQueuedSize;
        @SerializedName("total_wasted_session")
        private Integer totalWastedSession;
        @SerializedName("up_info_data")
        private Long upInfoData;
        @SerializedName("up_info_speed")
        private Integer upInfoSpeed;
        @SerializedName("up_rate_limit")
        private Integer upRateLimit;
        @SerializedName("use_alt_speed_limits")
        private Boolean useAltSpeedLimits;
        @SerializedName("use_subcategories")
        private Boolean useSubcategories;
        @SerializedName("write_cache_overload")
        private String writeCacheOverload;
    }
}
