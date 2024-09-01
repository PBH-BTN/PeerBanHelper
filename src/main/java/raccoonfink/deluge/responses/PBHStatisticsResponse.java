package raccoonfink.deluge.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.gson.reflect.TypeToken;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import raccoonfink.deluge.DelugeException;

import java.util.List;

@Getter
public class PBHStatisticsResponse extends DelugeResponse {
    private StatisticsResponseDTO statistics;

    public PBHStatisticsResponse(final Integer httpResponseCode, final JSONObject response) throws DelugeException {
        super(httpResponseCode, response);
        if (response.isNull("result")) {
            return;
        }
        JSONArray jsonArray = response.getJSONArray("result");
        String resultJson = jsonArray.toString();
        this.statistics = JsonUtil.getGson().fromJson(resultJson, StatisticsResponseDTO.class);
    }

    @NoArgsConstructor
    @Data
    public static class StatisticsResponseDTO{
        @JsonProperty("stats_last_timestamp")
        private Long statsLastTimestamp;
        @JsonProperty("total_payload_download")
        private Long totalPayloadDownload;
        @JsonProperty("total_payload_upload")
        private Long totalPayloadUpload;
        @JsonProperty("ip_overhead_download")
        private Long ipOverheadDownload;
        @JsonProperty("ip_overhead_upload")
        private Long ipOverheadUpload;
        @JsonProperty("tracker_download")
        private Long trackerDownload;
        @JsonProperty("tracker_upload")
        private Long trackerUpload;
        @JsonProperty("dht_download")
        private Long dhtDownload;
        @JsonProperty("dht_upload")
        private Long dhtUpload;
        @JsonProperty("total_wasted")
        private Long totalWasted;
        @JsonProperty("total_download")
        private Long totalDownload;
        @JsonProperty("total_upload")
        private Long totalUpload;
        @JsonProperty("payload_download_rate")
        private Double payloadDownloadRate;
        @JsonProperty("payload_upload_rate")
        private Double payloadUploadRate;
        @JsonProperty("download_rate")
        private Double downloadRate;
        @JsonProperty("upload_rate")
        private Double uploadRate;
        @JsonProperty("ip_overhead_download_rate")
        private Double ipOverheadDownloadRate;
        @JsonProperty("ip_overhead_upload_rate")
        private Double ipOverheadUploadRate;
        @JsonProperty("dht_download_rate")
        private Double dhtDownloadRate;
        @JsonProperty("dht_upload_rate")
        private Double dhtUploadRate;
        @JsonProperty("tracker_download_rate")
        private Double trackerDownloadRate;
        @JsonProperty("tracker_upload_rate")
        private Double trackerUploadRate;
        @JsonProperty("dht_nodes")
        private Long dhtNodes;
        @JsonProperty("disk_read_queue")
        private Long diskReadQueue;
        @JsonProperty("disk_write_queue")
        private Long diskWriteQueue;
        @JsonProperty("peers_count")
        private Long peersCount;
    }
}
