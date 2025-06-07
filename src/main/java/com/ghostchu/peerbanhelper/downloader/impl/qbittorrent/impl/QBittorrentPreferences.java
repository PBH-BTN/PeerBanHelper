package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public final class QBittorrentPreferences {
    @SerializedName("banned_IPs")
    private String bannedIps;

    @SerializedName("shadow_ban_enabled")
    private Boolean shadowBanEnabled;

    @SerializedName("up_limit")
    private Long upLimit;

    @SerializedName("dl_limit")
    private Long dlLimit;

    @SerializedName("alt_up_limit")
    private Long altUpLimit;

    @SerializedName("alt_dl_limit")
    private Long altDlLimit;
}
