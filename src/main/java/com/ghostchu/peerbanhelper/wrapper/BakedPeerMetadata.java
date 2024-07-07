package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.ipdb.IPGeoData;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class BakedPeerMetadata extends PeerMetadata {
    private IPGeoData geo;

    public BakedPeerMetadata(PeerMetadata banMetadata) {
        super(banMetadata.getDownloader(), banMetadata.getTorrent(), banMetadata.getPeer());
        PeerBanHelperServer.IPDBResponse resp = Main.getServer().queryIPDB(new PeerAddress(banMetadata.getPeer().getAddress().getIp(), banMetadata.getPeer().getAddress().getPort()));
        this.geo = resp.geoData().get();
    }
}