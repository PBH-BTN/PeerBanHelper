package com.ghostchu.peerbanhelper.btn.ping;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientPing {
    private String appId;
    private String appSecret;
    private String downloader;
    private long populateAt;
    private List<PeerConnection> peers;
}
