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
        @SerializedName(value = "enable_ipfilter", alternate = "enable_ip_filter")
        // 当我们丢弃 BitComet 2.10 和更旧版本时，删除 alternate
        private Boolean enableIpFilter;
        @SerializedName("enable_whitelist_mode") // 当我们丢弃 BitComet 2.10 和更旧版本时，删除 enable_whitelist_mode 字段
        private Boolean enableWhitelistMode;
        @SerializedName("ipfilter_mode") // BitComet 2.11 开始 enable_whitelist_mode 换成了这个名字
        private String filterMode;
        @SerializedName("loaded_record_count")
        private Integer loadedRecordCount;
    }
}
