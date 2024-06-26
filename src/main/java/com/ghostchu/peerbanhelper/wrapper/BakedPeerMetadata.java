package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;

@EqualsAndHashCode(callSuper = true)
@Data
public class BakedPeerMetadata extends PeerMetadata {
    private GeoWrapper geo;
    private ASNWrapper asn;

    public BakedPeerMetadata(@NotNull PeerMetadata banMetadata) {
        super(banMetadata.getDownloader(), banMetadata.getTorrent(), banMetadata.getPeer());
        PeerBanHelperServer.IPDBResponse resp = Main.getServer().queryIPDB(new PeerAddress(banMetadata.getPeer().getAddress().getIp(), banMetadata.getPeer().getAddress().getPort()));
        if (resp.cityResponse().get() != null) {
            this.geo = new GeoWrapper(resp.cityResponse().get());
        }
        if (resp.asnResponse().get() != null) {
            this.asn = new ASNWrapper(resp.asnResponse().get());
        }
    }
}