package com.ghostchu.peerbanhelper.btn.ping;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientPing {
    private UUID submitId;
    private String downloader;
    private long populateAt;
    private int batchIndex;
    private int batchSize;
    private List<PeerConnection> peers;
}
