package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Data
public class BanMetadata extends PeerMetadata implements Comparable<PeerMetadata>, Serializable {
    private String context;
    private long banAt;
    private long unbanAt;
    private BanBehavior banBehavior;
    private long uploadRate;
    private long downloadRate;
    private TranslationComponent rule;
    private TranslationComponent description;

    public BanMetadata(String context, String downloader, long banAt, long unbanAt, BanBehavior banBehavior, long uploadRate, long downloadRate, Torrent torrent, Peer peer, TranslationComponent rule,
                       TranslationComponent description) {
        super(downloader, torrent, peer);
        this.context = context;
        this.banAt = banAt;
        this.unbanAt = unbanAt;
        this.banBehavior = banBehavior;
        this.uploadRate = uploadRate;
        this.downloadRate = downloadRate;
        this.rule = rule;
        this.description = description;
    }

    public BanMetadata(String context, String downloader, long banAt, long unbanAt, BanBehavior banBehavior, long uploadRate, long downloadRate, TorrentWrapper torrent, PeerWrapper peer, TranslationComponent rule,
                       TranslationComponent description) {
        super(downloader, torrent, peer);
        this.context = context;
        this.banAt = banAt;
        this.unbanAt = unbanAt;
        this.banBehavior = banBehavior;
        this.uploadRate = uploadRate;
        this.downloadRate = downloadRate;
        this.rule = rule;
        this.description = description;
    }

    @Override
    public String toString() {
        return "BanMetadata{" +
                "context='" + context + '\'' +
                ", banAt=" + banAt +
                ", unbanAt=" + unbanAt +
                ", banBehavior=" + banBehavior +
                ", uploadRate=" + uploadRate +
                ", downloadRate=" + downloadRate +
                ", rule=" + tlUI(rule) +
                ", description=" + tlUI(description) +
                '}';
    }
}
