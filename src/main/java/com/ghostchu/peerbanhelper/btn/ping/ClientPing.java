package com.ghostchu.peerbanhelper.btn.ping;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientPing {
    private String submitId;
    private String downloader;
    private long populateAt;
    private int batchIndex;
    private int batchSize;
    private long bans;
    private List<PeerConnection> peers;
}
