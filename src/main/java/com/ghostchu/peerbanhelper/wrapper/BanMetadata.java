package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class BanMetadata extends PeerMetadata implements Comparable<PeerMetadata> {
    private String context;
    private long banAt;
    private long unbanAt;
    private String rule;
    private String description;

    public BanMetadata(@NotNull String context, @NotNull String downloader, long banAt, long unbanAt, @NotNull Torrent torrent, @NotNull Peer peer, String rule,
                       @NotNull String description) {
        super(downloader, torrent, peer);
        this.context = context;
        this.banAt = banAt;
        this.unbanAt = unbanAt;
        this.rule = rule;
        this.description = description;
    }

    public BanMetadata(@NotNull String context, @NotNull String downloader, long banAt, long unbanAt, @NotNull TorrentWrapper torrent, @NotNull PeerWrapper peer, @NotNull String rule,
                       @NotNull String description) {
        super(downloader, torrent, peer);
        this.context = context;
        this.banAt = banAt;
        this.unbanAt = unbanAt;
        this.rule = rule;
        this.description = description;
    }
}
