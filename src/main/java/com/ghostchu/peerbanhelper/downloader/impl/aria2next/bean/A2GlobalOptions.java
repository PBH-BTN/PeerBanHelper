package com.ghostchu.peerbanhelper.downloader.impl.aria2next.bean;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class A2GlobalOptions {
    @SerializedName("max-overall-download-limit")
    private Long maxDownloadLimit;
    @SerializedName("max-overall-upload-limit")
    private Long maxUploadLimit;
    @SerializedName("listen-port")
    private Integer listenPort;
    @SerializedName("max-overall-upload-limit")
    private Long maxOverallUploadLimit;
    @SerializedName("ax-overall-download-limit")
    private Long maxOverallDownloadLimit;

}
