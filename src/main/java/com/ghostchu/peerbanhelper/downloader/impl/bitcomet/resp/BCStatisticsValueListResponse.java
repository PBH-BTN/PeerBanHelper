package com.ghostchu.peerbanhelper.downloader.impl.bitcomet.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@Data
public class BCStatisticsValueListResponse {

    @JsonProperty("value_list")
    private List<ValueListDTO> valueList;

    @NoArgsConstructor
    @Data
    public static class ValueListDTO {
        @JsonProperty("token")
        private String token;
        @JsonProperty("value")
        private String value;
    }
}
