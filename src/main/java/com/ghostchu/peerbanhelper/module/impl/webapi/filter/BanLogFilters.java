package com.ghostchu.peerbanhelper.module.impl.webapi.filter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Filter parameters for ban log database queries.
 * Contains only filters that can be efficiently applied at the database level.
 * 
 * Geographic filters (country, city, ASN, ISP, netType) and context are not 
 * implemented as this data is not stored in the history table and would require 
 * expensive GeoIP lookups for each record, breaking pagination performance.
 */
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
    
    /**
     * Checks if any filter has a valid value.
     * 
     * @return true if at least one filter is set and not empty
     */
    public boolean hasAnyFilter() {
        return isValidFilter(reason) || isValidFilter(clientName) || isValidFilter(peerId) 
               || isValidFilter(torrentName) || isValidFilter(module) || isValidFilter(rule);
    }
    
    /**
     * Checks if a filter value is valid (not null and not empty after trimming).
     * 
     * @param value The filter value to check
     * @return true if the value is valid for filtering
     */
    private static boolean isValidFilter(String value) {
        return value != null && !value.trim().isEmpty();
    }
}