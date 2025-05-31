package raccoonfink.deluge.responses;

import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.json.JSONObject;
import raccoonfink.deluge.DelugeException;

@Getter
public final class PBHStatisticsResponse extends DelugeResponse {
    private StatisticsResponseDTO statistics;

    public PBHStatisticsResponse(final Integer httpResponseCode, final JSONObject response) throws DelugeException {
        super(httpResponseCode, response);
        if (response.isNull("result")) {
            return;
        }
        JSONObject jsonArray = response.getJSONObject("result");
        String resultJson = jsonArray.toString();
        this.statistics = JsonUtil.getGson().fromJson(resultJson, StatisticsResponseDTO.class);
    }

    @NoArgsConstructor
    @Data
    public static class StatisticsResponseDTO{
        @SerializedName("stats_last_timestamp")
        private Long statsLastTimestamp;
        @SerializedName("total_payload_download")
        private Long totalPayloadDownload;
        @SerializedName("total_payload_upload")
        private Long totalPayloadUpload;
        @SerializedName("ip_overhead_download")
        private Long ipOverheadDownload;
        @SerializedName("ip_overhead_upload")
        private Long ipOverheadUpload;
        @SerializedName("tracker_download")
        private Long trackerDownload;
        @SerializedName("tracker_upload")
        private Long trackerUpload;
        @SerializedName("dht_download")
        private Long dhtDownload;
        @SerializedName("dht_upload")
        private Long dhtUpload;
        @SerializedName("total_wasted")
        private Long totalWasted;
        @SerializedName("total_download")
        private Long totalDownload;
        @SerializedName("total_upload")
        private Long totalUpload;
        @SerializedName("payload_download_rate")
        private Double payloadDownloadRate;
        @SerializedName("payload_upload_rate")
        private Double payloadUploadRate;
        @SerializedName("download_rate")
        private Double downloadRate;
        @SerializedName("upload_rate")
        private Double uploadRate;
        @SerializedName("ip_overhead_download_rate")
        private Double ipOverheadDownloadRate;
        @SerializedName("ip_overhead_upload_rate")
        private Double ipOverheadUploadRate;
        @SerializedName("dht_download_rate")
        private Double dhtDownloadRate;
        @SerializedName("dht_upload_rate")
        private Double dhtUploadRate;
        @SerializedName("tracker_download_rate")
        private Double trackerDownloadRate;
        @SerializedName("tracker_upload_rate")
        private Double trackerUploadRate;
        @SerializedName("dht_nodes")
        private Long dhtNodes;
        @SerializedName("disk_read_queue")
        private Long diskReadQueue;
        @SerializedName("disk_write_queue")
        private Long diskWriteQueue;
        @SerializedName("peers_count")
        private Long peersCount;
    }
}
