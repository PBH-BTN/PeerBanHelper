package com.ghostchu.peerbanhelper.module.impl.webapi.filter;

import com.ghostchu.peerbanhelper.module.impl.webapi.dto.BanDTO;
import com.ghostchu.peerbanhelper.wrapper.BakedBanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerWrapper;

import java.util.Locale;

/**
 * Applies filters to ban list data in memory.
 * Since ban list data comes from active memory rather than database queries,
 * filtering is performed on the DTO objects after geo data enrichment.
 */
public class BanListFilterApplier {
    
    /**
     * Applies the given filters to a BanDTO object.
     * 
     * @param dto The ban DTO to check against filters
     * @param filters The filters to apply
     * @return true if the DTO passes all filters, false otherwise
     */
    public static boolean applyFilters(BanDTO dto, BanListFilters filters) {
        if (filters == null || !filters.hasAnyFilter()) {
            return true;
        }

        BakedBanMetadata metadata = dto.getBanMetadata();
        
        // Filter by reason/description
        if (!matchesStringFilter(metadata.getDescription(), filters.getReason())) {
            return false;
        }

        // Filter by client name
        PeerWrapper peer = metadata.getPeer();
        String clientName = peer != null ? peer.getClientName() : null;
        if (!matchesStringFilter(clientName, filters.getClientName())) {
            return false;
        }

        // Filter by peer ID
        String peerId = peer != null ? peer.getId() : null;
        if (!matchesStringFilter(peerId, filters.getPeerId())) {
            return false;
        }

        // Filter by geographic data
        var geoData = dto.getIpGeoData();
        if (geoData != null) {
            if (!matchesStringFilter(geoData.getCountry(), filters.getCountry()) ||
                !matchesStringFilter(geoData.getCity(), filters.getCity()) ||
                !matchesStringFilter(geoData.getAsn(), filters.getAsn()) ||
                !matchesStringFilter(geoData.getIsp(), filters.getIsp()) ||
                !matchesStringFilter(geoData.getNet(), filters.getNetType())) {
                return false;
            }
        } else {
            // If geo data is null but geo filters are set, exclude this entry
            if (hasGeoFilter(filters)) {
                return false;
            }
        }

        // Filter by context (maps to torrent ID for discovery location)
        String torrentId = null;
        if (metadata.getTorrent() != null) {
            torrentId = metadata.getTorrent().getId();
        }
        if (!matchesStringFilter(torrentId, filters.getContext())) {
            return false;
        }
        
        // Filter by rule
        if (!matchesStringFilter(metadata.getRule(), filters.getRule())) {
            return false;
        }

        return true;
    }
    
    /**
     * Checks if the actual value matches the filter value using case-insensitive contains.
     * 
     * @param actualValue The actual value from the data
     * @param filterValue The filter value to match against
     * @return true if filter passes (filter is empty or actual value contains filter value)
     */
    private static boolean matchesStringFilter(String actualValue, String filterValue) {
        if (filterValue == null || filterValue.trim().isEmpty()) {
            return true; // No filter applied
        }
        
        if (actualValue == null) {
            return false; // Cannot match against null value
        }
        
        return actualValue.toLowerCase(Locale.ROOT).contains(filterValue.trim().toLowerCase(Locale.ROOT));
    }
    
    /**
     * Checks if any geographic filter is set.
     * 
     * @param filters The filters to check
     * @return true if any geographic filter has a value
     */
    private static boolean hasGeoFilter(BanListFilters filters) {
        return isValidFilter(filters.getCountry()) || isValidFilter(filters.getCity()) ||
               isValidFilter(filters.getAsn()) || isValidFilter(filters.getIsp()) ||
               isValidFilter(filters.getNetType());
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