package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class Preferences {

    @SerializedName("banned_IPs")
    private String bannedIps;
}
