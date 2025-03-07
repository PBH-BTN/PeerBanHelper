package com.ghostchu.peerbanhelper.downloader.impl.bitcomet.resp;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@Data
public final class BCTaskPeersResponse {

    @SerializedName("error_code")
    private String errorCode;
    @SerializedName("peer_count")
    private PeerCountDTO peerCount;
    @SerializedName("peers")
    private List<PeersDTO> peers;
    @SerializedName("task")
    private TaskDTO task;

    @NoArgsConstructor
    @Data
    public static class PeerCountDTO {
        @SerializedName("peers_connected")
        private long peersConnected;
        @SerializedName("peers_connecting")
        private long peersConnecting;
    }

    @NoArgsConstructor
    @Data
    public static class TaskDTO {
        @SerializedName("task_id")
        private long taskId;
        @SerializedName("type")
        private String type;
        @SerializedName("task_name")
        private String taskName;
        @SerializedName("total_size")
        private long totalSize;
        @SerializedName("download_rate")
        private long downloadRate;
        @SerializedName("upload_rate")
        private long uploadRate;
        @SerializedName("permillage")
        private short permillage;
    }

    @NoArgsConstructor
    @Data
    public static class PeersDTO {
        @SerializedName("ip")
        private String ip;
        @SerializedName("client_type")
        private String clientType;
        @SerializedName("flag")
        private String flag;
        @SerializedName("remote_port")
        private int remotePort;
        @SerializedName("listen_port")
        private int listenPort;
        @SerializedName("permillage")
        private short permillage;
        @SerializedName("dl_rate")
        private long dlRate;
        @SerializedName("up_rate")
        private long upRate;
        @SerializedName("dl_size")
        private Long dlSize; // may null in some version, we need check it
        @SerializedName("up_size")
        private Long upSize; // may null in some version, we need check it
        @SerializedName("peer_id")
        private String peerId;
        @SerializedName("group")
        private String group;
    }
}
