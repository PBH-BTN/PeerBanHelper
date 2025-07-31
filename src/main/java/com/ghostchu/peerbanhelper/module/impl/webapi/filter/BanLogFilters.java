package com.ghostchu.peerbanhelper.module.impl.webapi.filter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BanLogFilters {
    private String reason;
    private String clientName;
    private String peerId;
    private String torrentName;
    private String module;
    private String rule;
    
    // Note: Geographic filters (country, city, ASN, ISP, netType) and context
    // are not implemented for ban log database queries as this data is not 
    // stored in the history table and would require GeoIP lookups
}