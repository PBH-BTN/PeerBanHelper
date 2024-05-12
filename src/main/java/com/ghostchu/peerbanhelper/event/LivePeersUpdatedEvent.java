package com.ghostchu.peerbanhelper.event;

import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.peerbanhelper.wrapper.PeerMetadata;
import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class LivePeersUpdatedEvent {
    private ImmutableMap<PeerAddress, PeerMetadata> livePeers;
}
