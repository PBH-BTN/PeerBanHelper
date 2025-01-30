package raccoonfink.deluge.responses;

import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import raccoonfink.deluge.DelugeException;

import java.util.List;

@Getter
public final class PBHActiveTorrentsResponse extends DelugeResponse {
    private List<ActiveTorrentsResponseDTO> activeTorrents;

    public PBHActiveTorrentsResponse(final Integer httpResponseCode, final JSONObject response) throws DelugeException {
        super(httpResponseCode, response);
        if (response.isNull("result")) {
            return;
        }
        JSONArray jsonArray = response.getJSONArray("result");
        String resultJson = jsonArray.toString();
        this.activeTorrents = JsonUtil.getGson().fromJson(resultJson, new TypeToken<List<ActiveTorrentsResponseDTO>>() {
        }.getType());
    }

    @NoArgsConstructor
    @Data
    public static class ActiveTorrentsResponseDTO {

        @SerializedName("id")
        private String id;
        @SerializedName("name")
        private String name;
        @SerializedName("info_hash")
        private String infoHash;
        @SerializedName("progress")
        private Double progress;
        @SerializedName("size")
        private Long size;
        @SerializedName("completed_size")
        private Long completedSize;
        @SerializedName("upload_payload_rate")
        private Long uploadPayloadRate;
        @SerializedName("download_payload_rate")
        private Long downloadPayloadRate;
        @SerializedName("priv")
        private Boolean priv;
        @SerializedName("peers")
        private List<PeersDTO> peers;

        @NoArgsConstructor
        @Data
        public static class PeersDTO {
            @SerializedName("ip")
            private String ip;
            @SerializedName("port")
            private Integer port;
            @SerializedName("peer_id")
            private String peerId;
            @SerializedName("client_name")
            private String clientName;
            @SerializedName("up_speed")
            private Long upSpeed;
            @SerializedName("down_speed")
            private Long downSpeed;
            @SerializedName("payload_up_speed")
            private Long payloadUpSpeed;
            @SerializedName("payload_down_speed")
            private Long payloadDownSpeed;
            @SerializedName("total_upload")
            private Long totalUpload;
            @SerializedName("total_download")
            private Long totalDownload;
            @SerializedName("progress")
            private Double progress;
            @SerializedName("flags")
            private Integer flags;
            @SerializedName("source")
            private Integer source;
            @SerializedName("local_endpoint_ip")
            private String localEndpointIp;
            @SerializedName("local_endpoint_port")
            private Integer localEndpointPort;
            @SerializedName("queue_bytes")
            private Long queueBytes;
            @SerializedName("request_timeout")
            private Long requestTimeout;
            @SerializedName("num_hashfails")
            private Long numHashfails;
            @SerializedName("download_queue_length")
            private Long downloadQueueLength;
            @SerializedName("upload_queue_length")
            private Long uploadQueueLength;
            @SerializedName("failcount")
            private Long failcount;
            @SerializedName("downloading_block_index")
            private Long downloadingBlockIndex;
            @SerializedName("downloading_progress")
            private Long downloadingProgress;
            @SerializedName("downloading_total")
            private Long downloadingTotal;
            @SerializedName("connection_type")
            private Long connectionType;
            @SerializedName("send_quota")
            private Long sendQuota;
            @SerializedName("receive_quota")
            private Long receiveQuota;
            @SerializedName("rtt")
            private Long rtt;
            @SerializedName("num_pieces")
            private Long numPieces;
            @SerializedName("download_rate_peak")
            private Long downloadRatePeak;
            @SerializedName("upload_rate_peak")
            private Long uploadRatePeak;
            @SerializedName("progress_ppm")
            private Long progressPpm;
        }
    }
}
