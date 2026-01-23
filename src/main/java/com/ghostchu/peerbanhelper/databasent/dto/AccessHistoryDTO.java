package com.ghostchu.peerbanhelper.databasent.dto;

import com.ghostchu.peerbanhelper.downloader.DownloaderBasicInfo;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.TorrentEntityDTO;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class AccessHistoryDTO {
    @SerializedName("id")
    private Long id;
    @SerializedName("address")
    private String address;
    @SerializedName("port")
    private Integer port;
    @SerializedName("torrent")
    private TorrentEntityDTO torrent;
    @SerializedName("downloader")
    private DownloaderBasicInfo downloader;
    @SerializedName("peerId")
    private String peerId;
    @SerializedName("clientName")
    private String clientName;
    @SerializedName("uploaded")
    private Long uploaded;
    @SerializedName("uploadedOffset")
    private Long uploadedOffset;
    @SerializedName("uploadSpeed")
    private Long uploadSpeed;
    @SerializedName("downloaded")
    private Long downloaded;
    @SerializedName("downloadedOffset")
    private Long downloadedOffset;
    @SerializedName("downloadSpeed")
    private Long downloadSpeed;
    @SerializedName("lastFlags")
    private String lastFlags;
    @SerializedName("firstTimeSeen")
    private OffsetDateTime firstTimeSeen;
    @SerializedName("lastTimeSeen")
    private OffsetDateTime lastTimeSeen;
}
