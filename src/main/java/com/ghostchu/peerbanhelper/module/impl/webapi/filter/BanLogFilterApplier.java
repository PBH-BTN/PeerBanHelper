package com.ghostchu.peerbanhelper.module.impl.webapi.filter;

import com.ghostchu.peerbanhelper.database.table.HistoryEntity;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;

import java.sql.SQLException;

public class BanLogFilterApplier {
    
    public static QueryBuilder<HistoryEntity, Long> applyFilters(QueryBuilder<HistoryEntity, Long> queryBuilder, BanLogFilters filters) throws SQLException {
        if (filters == null) return queryBuilder;

        Where<HistoryEntity, Long> where = queryBuilder.where();
        boolean hasWhere = false;

        // Filter by reason/description
        if (filters.getReason() != null && !filters.getReason().trim().isEmpty()) {
            if (hasWhere) where.and();
            where.like("description", "%" + sanitizeForLike(filters.getReason()) + "%");
            hasWhere = true;
        }

        // Filter by client name
        if (filters.getClientName() != null && !filters.getClientName().trim().isEmpty()) {
            if (hasWhere) where.and();
            where.like("peerClientName", "%" + sanitizeForLike(filters.getClientName()) + "%");
            hasWhere = true;
        }

        // Filter by peer ID
        if (filters.getPeerId() != null && !filters.getPeerId().trim().isEmpty()) {
            if (hasWhere) where.and();
            where.like("peerId", "%" + sanitizeForLike(filters.getPeerId()) + "%");
            hasWhere = true;
        }

        // Filter by torrent name
        if (filters.getTorrentName() != null && !filters.getTorrentName().trim().isEmpty()) {
            if (hasWhere) where.and();
            where.like("torrent.name", "%" + sanitizeForLike(filters.getTorrentName()) + "%");
            hasWhere = true;
        }

        // Filter by module
        if (filters.getModule() != null && !filters.getModule().trim().isEmpty()) {
            if (hasWhere) where.and();
            where.like("module.name", "%" + sanitizeForLike(filters.getModule()) + "%");
            hasWhere = true;
        }

        // Filter by rule
        if (filters.getRule() != null && !filters.getRule().trim().isEmpty()) {
            if (hasWhere) where.and();
            where.like("rule.rule", "%" + sanitizeForLike(filters.getRule()) + "%");
            hasWhere = true;
        }

        return queryBuilder;
    }
    
    /**
     * Basic sanitization for LIKE queries to prevent SQL injection.
     * Escapes special LIKE characters and limits input length.
     */
    private static String sanitizeForLike(String input) {
        if (input == null) return "";
        
        // Limit input length to prevent DoS
        String sanitized = input.length() > 100 ? input.substring(0, 100) : input;
        
        // Escape LIKE special characters
        sanitized = sanitized.replace("\\", "\\\\")
                           .replace("%", "\\%")
                           .replace("_", "\\_");
                           
        return sanitized;
    }
}