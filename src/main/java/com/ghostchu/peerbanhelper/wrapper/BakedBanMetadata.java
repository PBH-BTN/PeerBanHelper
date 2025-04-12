package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.ipdb.IPGeoData;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Data
@NoArgsConstructor
public final class BakedBanMetadata implements Serializable {
    private String downloader;
    private TorrentWrapper torrent;
    private PeerWrapper peer;
    private IPGeoData geo;
    private String reverseLookup = "N/A";
    private String context;
    private long banAt;
    private long unbanAt;
    private String rule;
    private String description;

    public BakedBanMetadata(String locale, BanMetadata banMetadata) {
        this.downloader = banMetadata.getDownloader();
        this.torrent = banMetadata.getTorrent();
        this.peer = banMetadata.getPeer();
        this.geo = banMetadata.getGeo();
        this.reverseLookup = banMetadata.getReverseLookup();
        this.context = banMetadata.getContext();
        this.banAt = banMetadata.getBanAt();
        this.unbanAt = banMetadata.getUnbanAt();
        this.rule = tl(locale, banMetadata.getRule());
        this.description = tl(locale, banMetadata.getDescription());
    }
}
