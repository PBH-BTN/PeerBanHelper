package com.ghostchu.peerbanhelper.downloader.impl.bitcomet.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@Data
public class BCStatisticsValueListResponse {

    @SerializedName("value_list")
    private List<ValueListDTO> valueList;

    @NoArgsConstructor
    @Data
    public static class ValueListDTO {
        @SerializedName("token")
        private String token;
        @SerializedName("value")
        private String value;
    }
}
