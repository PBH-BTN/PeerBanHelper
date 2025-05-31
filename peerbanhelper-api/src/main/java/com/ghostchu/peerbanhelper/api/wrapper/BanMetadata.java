package com.ghostchu.peerbanhelper.api.wrapper;

import com.ghostchu.peerbanhelper.api.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.api.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.api.downloader.DownloaderBasicInfo;
import com.ghostchu.peerbanhelper.api.text.TranslationComponent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;


@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Data
public class BanMetadata extends PeerMetadata implements Serializable {
    private String context;
    private long banAt;
    private long unbanAt;
    private boolean banForDisconnect;
    private TranslationComponent rule;
    private TranslationComponent description;

    public BanMetadata(String context, DownloaderBasicInfo downloader, long banAt, long unbanAt, boolean banForDisconnect, Torrent torrent, Peer peer, TranslationComponent rule,
                       TranslationComponent description) {
        super(downloader, torrent, peer);
        this.context = context;
        this.banAt = banAt;
        this.unbanAt = unbanAt;
        this.banForDisconnect = banForDisconnect;
        this.rule = rule;
        this.description = description;
    }

    public BanMetadata(String context, DownloaderBasicInfo downloader, long banAt, long unbanAt, boolean banForDisconnect, TorrentWrapper torrent, PeerWrapper peer, TranslationComponent rule,
                       TranslationComponent description) {
        super(downloader, torrent, peer);
        this.context = context;
        this.banAt = banAt;
        this.unbanAt = unbanAt;
        this.banForDisconnect = banForDisconnect;
        this.rule = rule;
        this.description = description;
    }

    @Override
    public String toString() {
        return "BanMetadata{" +
                "context='" + context + '\'' +
                ", banAt=" + banAt +
                ", unbanAt=" + unbanAt +
                ", banForDisconnect=" + banForDisconnect +
                ", rule=" + rule +
                ", description=" + description +
                '}';
    }
}
