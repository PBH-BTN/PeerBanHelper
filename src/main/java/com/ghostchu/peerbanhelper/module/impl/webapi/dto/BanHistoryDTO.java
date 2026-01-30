package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import com.ghostchu.peerbanhelper.downloader.DownloaderBasicInfo;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class BanHistoryDTO {

    @SerializedName("banAt")
    private OffsetDateTime banAt;
    @SerializedName("unbanAt")
    private OffsetDateTime unbanAt;
    @SerializedName("peerIp")
    private String peerIp;
    @SerializedName("peerPort")
    private Integer peerPort;
    @SerializedName("peerId")
    private String peerId;
    @SerializedName("peerClientName")
    private String peerClientName;
    @SerializedName("peerUploaded")
    private Long peerUploaded;
    @SerializedName("peerDownloaded")
    private Long peerDownloaded;
    @SerializedName("peerProgress")
    private Double peerProgress;
    @SerializedName("torrentInfoHash")
    private String torrentInfoHash;
    @SerializedName("torrentName")
    private String torrentName;
    @SerializedName("torrentSize")
    private Long torrentSize;
    @SerializedName("module")
    private String module;
    @SerializedName("rule")
    private String rule;
    @SerializedName("description")
    private String description;
    @SerializedName("downloader")
    private DownloaderBasicInfo downloader;
}