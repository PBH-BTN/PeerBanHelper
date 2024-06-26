package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;

@EqualsAndHashCode(callSuper = true)
@Data
public class BakedBanMetadata extends BanMetadata {
    private GeoWrapper geo;
    private ASNWrapper asn;

    public BakedBanMetadata(@NotNull BanMetadata banMetadata) {
        super(banMetadata.getContext(), banMetadata.getDownloader(), banMetadata.getBanAt(),
                banMetadata.getUnbanAt(), banMetadata.getTorrent(), banMetadata.getPeer(), banMetadata.getRule(),
                banMetadata.getDescription());
        PeerBanHelperServer.IPDBResponse resp = Main.getServer().queryIPDB(new PeerAddress(banMetadata.getPeer().getAddress().getIp(), banMetadata.getPeer().getAddress().getPort()));
        if (resp.cityResponse().get() != null) {
            this.geo = new GeoWrapper(resp.cityResponse().get());
            this.asn = new ASNWrapper(resp.asnResponse().get());
        }
    }
}