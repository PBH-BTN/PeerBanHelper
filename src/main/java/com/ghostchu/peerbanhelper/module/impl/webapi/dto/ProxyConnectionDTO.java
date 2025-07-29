package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class ProxyConnectionDTO {
    private String clientHost;
    private int clientPort;
    private String proxyHost;
    private int proxyPort;
    private String remoteHost;
    private int remotePort;
    private long establishedAt;
    private long lastActivityAt;
    private long uploaded;
    private long downloaded;
}
