package com.ghostchu.peerbanhelper.module.impl.webapi.filter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Filter parameters for ban list data.
 * These filters are applied in memory after geo data enrichment since ban list
 * data comes from active memory rather than database queries.
 */
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
    
    /**
     * Checks if any filter has a valid value.
     * 
     * @return true if at least one filter is set and not empty
     */
    public boolean hasAnyFilter() {
        return isValidFilter(reason) || isValidFilter(clientName) || isValidFilter(peerId) 
               || isValidFilter(country) || isValidFilter(city) || isValidFilter(asn)
               || isValidFilter(isp) || isValidFilter(netType) || isValidFilter(context)
               || isValidFilter(rule);
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