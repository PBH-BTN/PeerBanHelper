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
}
