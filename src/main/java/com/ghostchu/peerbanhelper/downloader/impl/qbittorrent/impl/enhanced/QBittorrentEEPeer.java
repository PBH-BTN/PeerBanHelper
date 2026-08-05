package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.enhanced;


import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.QBittorrentPeer;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public final class QBittorrentEEPeer extends QBittorrentPeer {
    @Getter
    @SerializedName("shadowbanned")
    private Boolean shadowBanned;
}
