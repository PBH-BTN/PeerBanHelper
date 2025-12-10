package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import com.ghostchu.peerbanhelper.util.ipdb.IPGeoData;

public record PeerInfoDTO(
        boolean found,
        String address,
        long firstTimeSeen,
        long lastTimeSeen,
        long banCount,
        long torrentAccessCount,
        long uploadedToPeer,
        long downloadedFromPeer,
        IPGeoData geo,
        String ptrLookup,
        boolean btnQueryAvailable
) {
}
