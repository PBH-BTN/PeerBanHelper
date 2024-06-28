package raccoonfink.deluge.responses;

import com.ghostchu.peerbanhelper.util.JsonUtil;
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
public class PBHActiveTorrentsResponse extends DelugeResponse {
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
        @SerializedName("upload_payload_rate")
        private Integer uploadPayloadRate;
        @SerializedName("download_payload_rate")
        private Integer downloadPayloadRate;
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
            private Integer upSpeed;
            @SerializedName("down_speed")
            private Integer downSpeed;
            @SerializedName("payload_up_speed")
            private Integer payloadUpSpeed;
            @SerializedName("payload_down_speed")
            private Integer payloadDownSpeed;
            @SerializedName("total_upload")
            private Integer totalUpload;
            @SerializedName("total_download")
            private Integer totalDownload;
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
            private Integer queueBytes;
            @SerializedName("request_timeout")
            private Integer requestTimeout;
            @SerializedName("num_hashfails")
            private Integer numHashfails;
            @SerializedName("download_queue_length")
            private Integer downloadQueueLength;
            @SerializedName("upload_queue_length")
            private Integer uploadQueueLength;
            @SerializedName("failcount")
            private Integer failcount;
            @SerializedName("downloading_block_index")
            private Integer downloadingBlockIndex;
            @SerializedName("downloading_progress")
            private Integer downloadingProgress;
            @SerializedName("downloading_total")
            private Integer downloadingTotal;
            @SerializedName("connection_type")
            private Integer connectionType;
            @SerializedName("send_quota")
            private Integer sendQuota;
            @SerializedName("receive_quota")
            private Integer receiveQuota;
            @SerializedName("rtt")
            private Integer rtt;
            @SerializedName("num_pieces")
            private Integer numPieces;
            @SerializedName("download_rate_peak")
            private Integer downloadRatePeak;
            @SerializedName("upload_rate_peak")
            private Integer uploadRatePeak;
            @SerializedName("progress_ppm")
            private Integer progressPpm;
        }
    }
}
