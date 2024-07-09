package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public final class Preferences {

    @SerializedName("banned_IPs")
    private String bannedIps;

}
