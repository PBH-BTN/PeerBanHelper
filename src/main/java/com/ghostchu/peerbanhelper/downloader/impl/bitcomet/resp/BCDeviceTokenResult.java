package com.ghostchu.peerbanhelper.downloader.impl.bitcomet.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class BCDeviceTokenResult {

    @JsonProperty("device_token")
    private String deviceToken;
    @JsonProperty("server_id")
    private String serverId;
    @JsonProperty("server_name")
    private String serverName;
    @JsonProperty("version")
    private String version;
}
