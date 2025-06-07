package com.ghostchu.peerbanhelper.downloader.impl.bitcomet.resp;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class BCConnectionConfigResponse {

    @SerializedName("connection_config")
    private ConnectionConfigDTO connectionConfig;
    @SerializedName("error_code")
    private String errorCode;
    @SerializedName("error_message")
    private String errorMessage;
    @SerializedName("version")
    private String version;

    @NoArgsConstructor
    @Data
    public static class ConnectionConfigDTO {
        @SerializedName("max_download_speed")
        private Integer maxDownloadSpeed;
        @SerializedName("max_upload_speed")
        private Integer maxUploadSpeed;
        @SerializedName("enable_listen_tcp")
        private Boolean enableListenTcp;
        @SerializedName("listen_port_tcp")
        private Integer listenPortTcp;
    }
}
