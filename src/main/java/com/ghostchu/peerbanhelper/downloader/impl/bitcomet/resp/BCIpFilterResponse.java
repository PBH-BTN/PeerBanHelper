package com.ghostchu.peerbanhelper.downloader.impl.bitcomet.resp;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public final class BCIpFilterResponse {

    @SerializedName("ip_filter_config")
    private IpFilterConfigDTO ipFilterConfig;
    @SerializedName("version")
    private String version;

    @NoArgsConstructor
    @Data
    public static class IpFilterConfigDTO {
        @SerializedName(value = "enable_ipfilter")
        private Boolean enableIpFilter;
        @SerializedName("ipfilter_mode")
        private String filterMode;
        @SerializedName("loaded_record_count")
        private Integer loadedRecordCount;
    }
}
