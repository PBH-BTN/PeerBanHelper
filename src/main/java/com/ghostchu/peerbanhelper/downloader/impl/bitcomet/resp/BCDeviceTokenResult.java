package com.ghostchu.peerbanhelper.downloader.impl.bitcomet.resp;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public final class BCDeviceTokenResult {

    @SerializedName("device_token")
    private String deviceToken;
    @SerializedName("server_id")
    private String serverId;
    @SerializedName("server_name")
    private String serverName;
    @SerializedName("version")
    private String version;
}
