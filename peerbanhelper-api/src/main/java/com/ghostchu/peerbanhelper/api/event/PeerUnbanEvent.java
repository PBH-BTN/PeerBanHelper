package com.ghostchu.peerbanhelper.api.event;

import com.ghostchu.peerbanhelper.api.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.api.wrapper.PeerAddress;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public final class PeerUnbanEvent {
    private PeerAddress peer;
    private BanMetadata banMetadata;
}
