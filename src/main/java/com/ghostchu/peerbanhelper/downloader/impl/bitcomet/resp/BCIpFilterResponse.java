package com.ghostchu.peerbanhelper.downloader.impl.bitcomet.resp;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class BCIpFilterResponse {

    @SerializedName("ip_filter_config")
    private IpFilterConfigDTO ipFilterConfig;
    @SerializedName("version")
    private String version;

    @NoArgsConstructor
    @Data
    public static class IpFilterConfigDTO {
        @SerializedName("enable_ip_filter")
        private Boolean enableIpFilter;
        @SerializedName("enable_whitelist_mode")
        private Boolean enableWhitelistMode;
        @SerializedName("loaded_record_count")
        private Integer loadedRecordCount;
    }
}
