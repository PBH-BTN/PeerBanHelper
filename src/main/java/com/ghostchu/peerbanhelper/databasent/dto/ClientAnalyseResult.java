package com.ghostchu.peerbanhelper.databasent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ClientAnalyseResult {
    private String peerId;
    private String clientName;
    private long uploaded;
    private long downloaded;
    private long count;
}
