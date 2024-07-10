package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class BanMetadata extends PeerMetadata implements Comparable<PeerMetadata>, Serializable {
    private String context;
    private long banAt;
    private long unbanAt;
    private TranslationComponent rule;
    private TranslationComponent description;

    public BanMetadata(String context, String downloader, long banAt, long unbanAt, Torrent torrent, Peer peer, TranslationComponent rule,
                       TranslationComponent description) {
        super(downloader, torrent, peer);
        this.context = context;
        this.banAt = banAt;
        this.unbanAt = unbanAt;
        this.rule = rule;
        this.description = description;
    }

    public BanMetadata(String context, String downloader, long banAt, long unbanAt, TorrentWrapper torrent, PeerWrapper peer, TranslationComponent rule,
                       TranslationComponent description) {
        super(downloader, torrent, peer);
        this.context = context;
        this.banAt = banAt;
        this.unbanAt = unbanAt;
        this.rule = rule;
        this.description = description;
    }
}
