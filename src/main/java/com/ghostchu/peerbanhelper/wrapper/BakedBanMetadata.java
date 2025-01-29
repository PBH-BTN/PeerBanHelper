package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.ipdb.IPGeoData;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.UUID;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Data
@NoArgsConstructor
public final class BakedBanMetadata implements Comparable<BakedBanMetadata>, Serializable {
    private String downloader;
    private UUID randomId;
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
        this.randomId = banMetadata.getRandomId();
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

    @Override
    public int compareTo(@NotNull BakedBanMetadata o) {
        return this.randomId.compareTo(o.randomId);
    }
}
