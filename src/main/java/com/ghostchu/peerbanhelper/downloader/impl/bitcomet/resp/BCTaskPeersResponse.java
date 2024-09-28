package com.ghostchu.peerbanhelper.downloader.impl.bitcomet.resp;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@Data
public class BCTaskPeersResponse {

    @SerializedName("error_code")
    private String errorCode;
    @SerializedName("peer_count")
    private PeerCountDTO peerCount;
    @SerializedName("peers")
    private List<PeersDTO> peers;
    @SerializedName("task")
    private TaskDTO task;
    @SerializedName("version")
    private String version;

    @NoArgsConstructor
    @Data
    public static class PeerCountDTO {
        @SerializedName("peers_connected")
        private Long peersConnected;
        @SerializedName("peers_connecting")
        private Long peersConnecting;
        @SerializedName("ltseed_connected")
        private Long ltseedConnected;
        @SerializedName("ltseed_connecting")
        private Long ltseedConnecting;
    }

    @NoArgsConstructor
    @Data
    public static class TaskDTO {
        @SerializedName("task_id")
        private Long taskId;
        @SerializedName("task_guid")
        private String taskGuid;
        @SerializedName("type")
        private String type;
        @SerializedName("task_name")
        private String taskName;
        @SerializedName("status")
        private String status;
        @SerializedName("total_size")
        private Long totalSize;
        @SerializedName("selected_size")
        private Long selectedSize;
        @SerializedName("selected_downloaded_size")
        private Long selectedDownloadedSize;
        @SerializedName("download_rate")
        private Long downloadRate;
        @SerializedName("upload_rate")
        private Long uploadRate;
        @SerializedName("error_code")
        private String errorCode;
        @SerializedName("error_message")
        private String errorMessage;
        @SerializedName("permillage")
        private Integer permillage;
        @SerializedName("left_time")
        private String leftTime;
    }

    @NoArgsConstructor
    @Data
    public static class PeersDTO {
        @SerializedName("ip")
        private String ip;
        @SerializedName("progress")
        private String progress;
        @SerializedName("dl_speed")
        private String dlSpeed;
        @SerializedName("up_speed")
        private String upSpeed;
        @SerializedName("client_type")
        private String clientType;
        @SerializedName("flag")
        private String flag;
        @SerializedName("log_id")
        private Long logId;
        @SerializedName("remote_port")
        private Integer remotePort;
        @SerializedName("listen_port")
        private Integer listenPort;
        @SerializedName("permillage")
        private Long permillage;
        @SerializedName("dl_rate")
        private Long dlRate;
        @SerializedName("up_rate")
        private Long upRate;
        @SerializedName("dl_size")
        private Long dlSize;
        @SerializedName("up_size")
        private Long upSize;
        @SerializedName("peer_id")
        private String peerId;
    }
}
