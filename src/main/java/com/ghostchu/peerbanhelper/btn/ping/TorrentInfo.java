package com.ghostchu.peerbanhelper.btn.ping;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Data
@NoArgsConstructor
public class TorrentInfo {
    private String identifier;
    private long size;
}
