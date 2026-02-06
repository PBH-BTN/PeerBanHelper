package com.ghostchu.peerbanhelper.databasent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TorrentCount {
    private Long torrentId;
    private Long count;
}
