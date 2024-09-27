package com.ghostchu.peerbanhelper.downloader.impl.bitcomet.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@Data
public class BCTaskPeersResponse {

    @JsonProperty("error_code")
    private String errorCode;
    @JsonProperty("peer_count")
    private PeerCountDTO peerCount;
    @JsonProperty("peers")
    private List<PeersDTO> peers;
    @JsonProperty("task")
    private TaskDTO task;
    @JsonProperty("version")
    private String version;

    @NoArgsConstructor
    @Data
    public static class PeerCountDTO {
        @JsonProperty("peers_connected")
        private Integer peersConnected;
        @JsonProperty("peers_connecting")
        private Integer peersConnecting;
        @JsonProperty("ltseed_connected")
        private Integer ltseedConnected;
        @JsonProperty("ltseed_connecting")
        private Integer ltseedConnecting;
    }

    @NoArgsConstructor
    @Data
    public static class TaskDTO {
        @JsonProperty("task_id")
        private Integer taskId;
        @JsonProperty("task_guid")
        private String taskGuid;
        @JsonProperty("type")
        private String type;
        @JsonProperty("task_name")
        private String taskName;
        @JsonProperty("status")
        private String status;
        @JsonProperty("total_size")
        private Long totalSize;
        @JsonProperty("selected_size")
        private Long selectedSize;
        @JsonProperty("selected_downloaded_size")
        private Integer selectedDownloadedSize;
        @JsonProperty("download_rate")
        private Integer downloadRate;
        @JsonProperty("upload_rate")
        private Integer uploadRate;
        @JsonProperty("error_code")
        private String errorCode;
        @JsonProperty("error_message")
        private String errorMessage;
        @JsonProperty("permillage")
        private Integer permillage;
        @JsonProperty("left_time")
        private String leftTime;
    }

    @NoArgsConstructor
    @Data
    public static class PeersDTO {
        @JsonProperty("ip")
        private String ip;
        @JsonProperty("progress")
        private String progress;
        @JsonProperty("dl_speed")
        private String dlSpeed;
        @JsonProperty("up_speed")
        private String upSpeed;
        @JsonProperty("client_type")
        private String clientType;
        @JsonProperty("flag")
        private String flag;
        @JsonProperty("log_id")
        private Integer logId;
        @JsonProperty("remote_port")
        private Integer remotePort;
        @JsonProperty("listen_port")
        private Integer listenPort;
        @JsonProperty("permillage")
        private Integer permillage;
        @JsonProperty("dl_rate")
        private Long dlRate;
        @JsonProperty("up_rate")
        private Long upRate;
        @JsonProperty("peer_id")
        private String peerId;
    }
}
