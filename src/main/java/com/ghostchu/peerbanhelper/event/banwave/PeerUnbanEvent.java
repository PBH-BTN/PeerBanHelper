package com.ghostchu.peerbanhelper.event.banwave;

import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import inet.ipaddr.IPAddress;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@AllArgsConstructor
@Data
public final class PeerUnbanEvent {
    private final Map<IPAddress, BanMetadata> unbannedPeers;
}
