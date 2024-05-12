package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class BanMetadata extends PeerMetadata implements Comparable<PeerMetadata> {
    private String context;
    private long banAt;
    private long unbanAt;
    private String rule;
    private String description;

    public BanMetadata(String context, String downloader, long banAt, long unbanAt, Torrent torrent, Peer peer, String rule,
                       String description, @Nullable CityResponse cityResponse, @Nullable AsnResponse asnResponse) {
        super(downloader, torrent, peer, cityResponse, asnResponse);
        this.context = context;
        this.banAt = banAt;
        this.unbanAt = unbanAt;
        this.rule = rule;
        this.description = description;
    }

}
