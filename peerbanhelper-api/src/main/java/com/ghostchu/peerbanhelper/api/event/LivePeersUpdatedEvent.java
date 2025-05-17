package com.ghostchu.peerbanhelper.api.event;

import com.ghostchu.peerbanhelper.api.wrapper.PeerAddress;
import com.ghostchu.peerbanhelper.api.wrapper.PeerMetadata;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Data
public final class LivePeersUpdatedEvent {
    private Map<PeerAddress, List<PeerMetadata>> livePeers;
}
