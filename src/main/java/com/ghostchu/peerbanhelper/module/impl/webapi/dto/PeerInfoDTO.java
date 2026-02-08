package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import com.ghostchu.peerbanhelper.util.ipdb.IPGeoData;

import java.time.OffsetDateTime;

public record PeerInfoDTO(
        boolean found,
        String address,
        OffsetDateTime firstTimeSeen,
        OffsetDateTime lastTimeSeen,
        long banCount,
        long torrentAccessCount,
        long uploadedToPeer,
        long downloadedFromPeer,
        IPGeoData geo,
        String ptrLookup,
        boolean btnQueryAvailable
) {
}
