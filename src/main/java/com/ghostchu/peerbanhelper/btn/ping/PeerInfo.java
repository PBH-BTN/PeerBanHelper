package com.ghostchu.peerbanhelper.btn.ping;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class PeerInfo {
    private PeerAddress address;
    private String peerId;
    private String clientName;
    private String flag;
    private long downloaded;
    private long rtDownloadSpeed;
    private long uploaded;
    private long rtUploadSpeed;
    private double progress;
}
