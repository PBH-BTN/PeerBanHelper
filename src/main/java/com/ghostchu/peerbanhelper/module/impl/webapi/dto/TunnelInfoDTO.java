package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class TunnelInfoDTO {
    private boolean valid;
    private long startedAt;
    private long lastSuccessHeartbeatAt;
    private long connectionsHandled;
    private long connectionsFailed;
    private long totalToDownstreamBytes;
    private long totalToUpstreamBytes;
    private long establishedConnections;
    private String proxyHost;
    private int proxyPort;
    private String upstreamHost;
    private int upstreamPort;
}
