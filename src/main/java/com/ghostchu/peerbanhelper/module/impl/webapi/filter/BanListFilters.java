package com.ghostchu.peerbanhelper.module.impl.webapi.filter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BanListFilters {
    private String reason;
    private String clientName;
    private String peerId;
    private String country;
    private String city;
    private String asn;
    private String isp;
    private String netType;
    private String context;
    private String rule;
}