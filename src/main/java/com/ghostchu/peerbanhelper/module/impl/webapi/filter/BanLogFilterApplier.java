package com.ghostchu.peerbanhelper.module.impl.webapi.filter;

import com.ghostchu.peerbanhelper.database.table.HistoryEntity;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.stmt.Where;

import java.sql.SQLException;

/**
 * Applies filters to ban log database queries using safe parameterized queries.
 * This class ensures SQL injection prevention by using ORMLite's SelectArg for all user input.
 */
public class BanLogFilterApplier {
    
    /**
     * Applies the given filters to a QueryBuilder for HistoryEntity.
     * Uses parameterized queries with SelectArg to prevent SQL injection.
     *
     * @param queryBuilder The query builder to apply filters to
     * @param filters The filters to apply
     * @return The modified query builder with filters applied
     * @throws SQLException if there's an error building the query
     */
    public static QueryBuilder<HistoryEntity, Long> applyFilters(QueryBuilder<HistoryEntity, Long> queryBuilder, BanLogFilters filters) throws SQLException {
        if (filters == null || !filters.hasAnyFilter()) {
            return queryBuilder;
        }

        Where<HistoryEntity, Long> where = queryBuilder.where();
        boolean hasWhere = false;

        // Filter by reason/description - using SelectArg for safe parameterized query
        if (isValidFilterValue(filters.getReason())) {
            if (hasWhere) where.and();
            where.like("description", new SelectArg("%" + sanitizeInput(filters.getReason()) + "%"));
            hasWhere = true;
        }

        // Filter by client name
        if (isValidFilterValue(filters.getClientName())) {
            if (hasWhere) where.and();
            where.like("peerClientName", new SelectArg("%" + sanitizeInput(filters.getClientName()) + "%"));
            hasWhere = true;
        }

        // Filter by peer ID
        if (isValidFilterValue(filters.getPeerId())) {
            if (hasWhere) where.and();
            where.like("peerId", new SelectArg("%" + sanitizeInput(filters.getPeerId()) + "%"));
            hasWhere = true;
        }

        // Filter by torrent name
        if (isValidFilterValue(filters.getTorrentName())) {
            if (hasWhere) where.and();
            where.like("torrent.name", new SelectArg("%" + sanitizeInput(filters.getTorrentName()) + "%"));
            hasWhere = true;
        }

        // Filter by module
        if (isValidFilterValue(filters.getModule())) {
            if (hasWhere) where.and();
            where.like("module.name", new SelectArg("%" + sanitizeInput(filters.getModule()) + "%"));
            hasWhere = true;
        }

        // Filter by rule
        if (isValidFilterValue(filters.getRule())) {
            if (hasWhere) where.and();
            where.like("rule.rule", new SelectArg("%" + sanitizeInput(filters.getRule()) + "%"));
            hasWhere = true;
        }

        // Filter by context (discovery location) - maps to downloader field
        if (isValidFilterValue(filters.getContext())) {
            if (hasWhere) where.and();
            where.like("downloader", new SelectArg("%" + sanitizeInput(filters.getContext()) + "%"));
            hasWhere = true;
        }

        return queryBuilder;
    }
    
    /**
     * Checks if a filter value is valid (not null and not empty after trimming).
     *
     * @param value The filter value to check
     * @return true if the value is valid for filtering
     */
    private static boolean isValidFilterValue(String value) {
        return value != null && !value.trim().isEmpty();
    }
    
    /**
     * Sanitizes input for LIKE queries by limiting length and escaping special characters.
     * This provides defense in depth even though we use parameterized queries.
     *
     * @param input The input string to sanitize
     * @return The sanitized string
     */
    private static String sanitizeInput(String input) {
        if (input == null) {
            return "";
        }
        
        // Trim whitespace and limit input length to prevent DoS
        String sanitized = input.trim();
        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 100);
        }
        
        // Escape LIKE special characters for additional safety
        sanitized = sanitized.replace("\\", "\\\\")
                           .replace("%", "\\%")
                           .replace("_", "\\_");
                           
        return sanitized;
    }
}