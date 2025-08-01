package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class TunnelProxyConnectionDTO {
    private String downstreamHost;
    private int downstreamPort;
    private String proxyHost;
    private int proxyPort;
    private String proxyOutgoingHost;
    private int proxyOutgoingPort;
    private String upstreamHost;
    private int upstreamPort;
    private long establishedAt;
    private long lastActivityAt;
    private long toDownstreamBytes;
    private long toUpstreamBytes;
}
