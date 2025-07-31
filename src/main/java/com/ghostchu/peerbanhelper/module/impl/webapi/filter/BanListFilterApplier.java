package com.ghostchu.peerbanhelper.module.impl.webapi.filter;

import com.ghostchu.peerbanhelper.module.impl.webapi.dto.BanDTO;
import com.ghostchu.peerbanhelper.wrapper.BakedBanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerWrapper;

import java.util.Locale;

public class BanListFilterApplier {
    
    public static boolean applyFilters(BanDTO dto, BanListFilters filters) {
        if (filters == null) return true;

        BakedBanMetadata metadata = dto.getBanMetadata();
        
        // Filter by reason/description
        if (filters.getReason() != null && !filters.getReason().trim().isEmpty()) {
            String description = metadata.getDescription();
            if (description == null || !description.toLowerCase(Locale.ROOT).contains(filters.getReason().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        // Filter by client name
        if (filters.getClientName() != null && !filters.getClientName().trim().isEmpty()) {
            PeerWrapper peer = metadata.getPeer();
            String clientName = peer != null ? peer.getClientName() : null;
            if (clientName == null || !clientName.toLowerCase(Locale.ROOT).contains(filters.getClientName().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        // Filter by peer ID
        if (filters.getPeerId() != null && !filters.getPeerId().trim().isEmpty()) {
            PeerWrapper peer = metadata.getPeer();
            String peerId = peer != null ? peer.getId() : null;
            if (peerId == null || !peerId.toLowerCase(Locale.ROOT).contains(filters.getPeerId().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        // Filter by country
        if (filters.getCountry() != null && !filters.getCountry().trim().isEmpty()) {
            var geoData = dto.getIpGeoData();
            String country = geoData != null ? geoData.getCountry() : null;
            if (country == null || !country.toLowerCase(Locale.ROOT).contains(filters.getCountry().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        // Filter by city
        if (filters.getCity() != null && !filters.getCity().trim().isEmpty()) {
            var geoData = dto.getIpGeoData();
            String city = geoData != null ? geoData.getCity() : null;
            if (city == null || !city.toLowerCase(Locale.ROOT).contains(filters.getCity().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        // Filter by ASN
        if (filters.getAsn() != null && !filters.getAsn().trim().isEmpty()) {
            var geoData = dto.getIpGeoData();
            String asn = geoData != null ? geoData.getAsn() : null;
            if (asn == null || !asn.toLowerCase(Locale.ROOT).contains(filters.getAsn().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        // Filter by ISP
        if (filters.getIsp() != null && !filters.getIsp().trim().isEmpty()) {
            var geoData = dto.getIpGeoData();
            String isp = geoData != null ? geoData.getIsp() : null;
            if (isp == null || !isp.toLowerCase(Locale.ROOT).contains(filters.getIsp().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        // Filter by network type
        if (filters.getNetType() != null && !filters.getNetType().trim().isEmpty()) {
            var geoData = dto.getIpGeoData();
            String netType = geoData != null ? geoData.getNet() : null;
            if (netType == null || !netType.toLowerCase(Locale.ROOT).contains(filters.getNetType().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        // Filter by context
        if (filters.getContext() != null && !filters.getContext().trim().isEmpty()) {
            String context = metadata.getContext();
            if (context == null || !context.toLowerCase(Locale.ROOT).contains(filters.getContext().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        // Filter by rule
        if (filters.getRule() != null && !filters.getRule().trim().isEmpty()) {
            String rule = metadata.getRule();
            if (rule == null || !rule.toLowerCase(Locale.ROOT).contains(filters.getRule().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        return true;
    }
}