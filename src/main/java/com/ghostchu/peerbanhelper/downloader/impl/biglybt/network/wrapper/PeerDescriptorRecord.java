package com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class PeerDescriptorRecord {
    private String ip;
    private int tcpPort;
    private int udpPort;
    private boolean useCrypto;
    private String peerSource;
}
