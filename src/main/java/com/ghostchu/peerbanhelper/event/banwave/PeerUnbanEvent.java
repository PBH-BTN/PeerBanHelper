package com.ghostchu.peerbanhelper.event.banwave;

import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import inet.ipaddr.IPAddress;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public final class PeerUnbanEvent {
    private IPAddress address;
    private BanMetadata banMetadata;
}
