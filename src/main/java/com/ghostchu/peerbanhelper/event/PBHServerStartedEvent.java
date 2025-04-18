package com.ghostchu.peerbanhelper.event;

import com.ghostchu.peerbanhelper.PeerBanHelper;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public final class PBHServerStartedEvent {
    private PeerBanHelper instance;
}
