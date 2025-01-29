package com.ghostchu.peerbanhelper.downloader.impl.bitcomet.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@Data
public final class BCTaskTrackersResponse {

    @JsonProperty("error_code")
    private String errorCode;
    @JsonProperty("trackers")
    private List<TrackersDTO> trackers;
    @JsonProperty("version")
    private String version;

    @NoArgsConstructor
    @Data
    public static class TrackersDTO {
        @JsonProperty("name")
        private String name;
        @JsonProperty("retries")
        private Integer retries;
        @JsonProperty("time_left")
        private String timeLeft;
        @JsonProperty("seeders")
        private Integer seeders;
        @JsonProperty("leechers")
        private Integer leechers;
        @JsonProperty("peers")
        private Integer peers;
        @JsonProperty("downloaded")
        private Integer downloaded;
        @JsonProperty("status")
        private String status;
        @JsonProperty("flag")
        private String flag;
    }
}
