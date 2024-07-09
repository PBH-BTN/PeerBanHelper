package com.ghostchu.peerbanhelper.wrapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public final class PeerAddressWrapper {
    private int port;
    private String ip;

    public PeerAddressWrapper(PeerAddress address) {
        this.ip = address.getIp();
        this.port = address.getPort();
    }
}
