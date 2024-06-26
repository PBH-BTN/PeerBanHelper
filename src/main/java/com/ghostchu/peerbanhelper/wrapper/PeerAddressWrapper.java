package com.ghostchu.peerbanhelper.wrapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PeerAddressWrapper {
    private int port;
    private String ip;

    public PeerAddressWrapper(@NotNull PeerAddress address) {
        this.ip = address.getIp();
        this.port = address.getPort();
    }
}
