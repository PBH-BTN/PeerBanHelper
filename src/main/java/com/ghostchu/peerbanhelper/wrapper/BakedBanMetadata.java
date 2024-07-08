package com.ghostchu.peerbanhelper.wrapper;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class BakedBanMetadata extends BanMetadata {

    public BakedBanMetadata(BanMetadata banMetadata) {
        super(banMetadata.getContext(), banMetadata.getDownloader(), banMetadata.getBanAt(),
                banMetadata.getUnbanAt(), banMetadata.getTorrent(), banMetadata.getPeer(), banMetadata.getRule(),
                banMetadata.getDescription());
    }
}