package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import com.ghostchu.peerbanhelper.ipdb.IPGeoData;
import com.ghostchu.peerbanhelper.wrapper.PeerWrapper;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Peer DTO 对象，填充了额外的信息
 */
@AllArgsConstructor
@Data
public final class PopulatedPeerDTO {
    private PeerWrapper peer;
    private IPGeoData geo;
    private String ptrRecord;
}
