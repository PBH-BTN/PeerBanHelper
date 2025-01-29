package com.ghostchu.peerbanhelper.event;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public final class PBHServerStartedEvent {
    private PeerBanHelperServer instance;
}
