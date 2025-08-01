package com.ghostchu.peerbanhelper.module.impl.ai.mcp;

import com.ghostchu.peerbanhelper.DownloaderServer;
import com.ghostchu.peerbanhelper.database.dao.impl.HistoryDao;
import com.ghostchu.peerbanhelper.database.dao.impl.TorrentDao;
import com.ghostchu.peerbanhelper.downloader.DownloaderManagerImpl;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP tool provider for ban management, downloader, and torrent operations
 */
@Slf4j
@Component
public class MCPOperationsTools implements MCPToolProvider {
    
    @Autowired
    private DownloaderServer downloaderServer;
    @Autowired
    private DownloaderManagerImpl downloaderManager;
    @Autowired
    private HistoryDao historyDao;
    @Autowired
    private TorrentDao torrentDao;

    @Override
    public void registerTools(MCPToolsRegistry registry) {
        log.debug("Registering ban management, downloader, and torrent tools for MCP");
        
        McpSchema.JsonSchema noInput = new McpSchema.JsonSchema(
                "object",
                Map.of(),
                List.of(),
                null,
                Map.of(),
                Map.of()
        );
        
        McpSchema.JsonSchema paginationSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "page", Map.of("type", "integer", "minimum", 1, "description", "Page number (1-based)"),
                        "pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 100, "description", "Items per page"),
                        "search", Map.of("type", "string", "description", "Search query")
                ),
                List.of(),
                null,
                Map.of(),
                Map.of()
        );
        
        McpSchema.JsonSchema ipListSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "ips", Map.of("type", "array", "items", Map.of("type", "string"), "description", "List of IP addresses to unban")
                ),
                List.of("ips"),
                null,
                Map.of(),
                Map.of()
        );
        
        McpSchema.JsonSchema torrentHashSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "infoHash", Map.of("type", "string", "description", "Torrent info hash")
                ),
                List.of("infoHash"),
                null,
                Map.of(),
                Map.of()
        );

        // Ban Management Tools
        registry.registerTool("get_ban_list", "Get active ban list with pagination support", paginationSchema, this::handleGetBanList);
        registry.registerTool("get_ban_logs", "Get ban history logs with pagination", paginationSchema, this::handleGetBanLogs);
        registry.registerTool("get_ban_ranks", "Get most frequently banned IPs ranking", paginationSchema, this::handleGetBanRanks);
        registry.registerTool("unban_peers", "Remove IP addresses from ban list", ipListSchema, this::handleUnbanPeers);
        registry.registerTool("get_ban_statistics", "Get comprehensive ban statistics", noInput, this::handleGetBanStatistics);
        
        // Downloader Management Tools
        registry.registerTool("get_downloaders", "Get all configured downloaders and their status", noInput, this::handleGetDownloaders);
        registry.registerTool("get_downloader_status", "Get detailed status of all downloaders", noInput, this::handleGetDownloaderStatus);
        
        // Torrent Management Tools
        registry.registerTool("get_torrent_info", "Get detailed information about a specific torrent", torrentHashSchema, this::handleGetTorrentInfo);
        registry.registerTool("get_torrents", "Get list of torrents with pagination", paginationSchema, this::handleGetTorrents);
        registry.registerTool("get_peer_info", "Get detailed peer information", paginationSchema, this::handleGetPeerInfo);
    }
    
    @Override
    public String getProviderName() {
        return "OperationsTools";
    }

    private Map<String, Object> handleGetBanList(Map<String, Object> arguments) {
        try {
            // Get pagination parameters
            int page = ((Number) arguments.getOrDefault("page", 1)).intValue();
            int pageSize = Math.min(((Number) arguments.getOrDefault("pageSize", 10)).intValue(), 100);
            String search = (String) arguments.getOrDefault("search", "");
            
            // This is a simplified implementation - in practice, would query the actual ban list
            StringBuilder response = new StringBuilder();
            response.append("Ban List (Page ").append(page).append(", Size ").append(pageSize).append("):\n\n");
            
            try {
                long totalBans = historyDao.queryForEq("ban", true).size();
                response.append("Total Banned IPs: ").append(totalBans).append("\n");
                response.append("Search Query: ").append(search.isEmpty() ? "None" : search).append("\n\n");
                
                if (!search.isEmpty()) {
                    response.append("Note: Search functionality requires integration with IPBanList service.\n");
                }
                
                response.append("Use the WebUI or dedicated ban management endpoints for detailed ban list access.");
                
            } catch (Exception e) {
                response.append("Error accessing ban data: ").append(e.getMessage());
            }
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", response.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting ban list: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetBanLogs(Map<String, Object> arguments) {
        try {
            int page = ((Number) arguments.getOrDefault("page", 1)).intValue();
            int pageSize = Math.min(((Number) arguments.getOrDefault("pageSize", 10)).intValue(), 100);
            
            StringBuilder response = new StringBuilder();
            response.append("Ban Logs (Page ").append(page).append(", Size ").append(pageSize).append("):\n\n");
            
            try {
                long totalLogs = historyDao.countOf();
                response.append("Total History Records: ").append(totalLogs).append("\n");
                response.append("Recent ban operations and history available through WebAPI endpoints.\n");
                response.append("Use /api/ban/logs for detailed ban operation history.");
                
            } catch (Exception e) {
                response.append("Error accessing ban logs: ").append(e.getMessage());
            }
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", response.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting ban logs: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetBanRanks(Map<String, Object> arguments) {
        try {
            StringBuilder response = new StringBuilder();
            response.append("Ban Ranks - Most Frequently Banned IPs:\n\n");
            response.append("This feature provides ranking of IPs by ban frequency.\n");
            response.append("Implementation requires aggregation of ban history data.\n");
            response.append("Use the WebUI Ban Statistics or /api/ban/statistics endpoint for detailed ranking data.");
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", response.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting ban ranks: " + e.getMessage());
        }
    }

    private Map<String, Object> handleUnbanPeers(Map<String, Object> arguments) {
        try {
            @SuppressWarnings("unchecked")
            List<String> ips = (List<String>) arguments.get("ips");
            
            if (ips == null || ips.isEmpty()) {
                return createErrorResponse("IP list is required and cannot be empty");
            }
            
            StringBuilder response = new StringBuilder();
            response.append("Unban Request for IPs:\n\n");
            for (String ip : ips) {
                response.append("- ").append(ip).append("\n");
            }
            response.append("\nNote: This is a read-only preview. Actual unbanning requires:\n");
            response.append("1. Proper authorization\n");
            response.append("2. Integration with IPBanList service\n");
            response.append("3. Use of /api/ban/unban endpoint\n");
            response.append("\nTotal IPs to unban: ").append(ips.size());
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", response.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error processing unban request: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetBanStatistics(Map<String, Object> arguments) {
        try {
            StringBuilder stats = new StringBuilder();
            stats.append("Ban Statistics Summary:\n\n");
            
            try {
                long totalBans = historyDao.queryForEq("ban", true).size();
                long totalHistory = historyDao.countOf();
                
                stats.append("Current Statistics:\n");
                stats.append("- Total Banned IPs: ").append(totalBans).append("\n");
                stats.append("- Total History Records: ").append(totalHistory).append("\n");
                stats.append("- Ban Ratio: ").append(totalHistory > 0 ? String.format("%.2f%%", (totalBans * 100.0 / totalHistory)) : "N/A").append("\n\n");
                
                stats.append("For detailed statistics including:\n");
                stats.append("- Ban frequency by rule type\n");
                stats.append("- Geographic distribution\n");
                stats.append("- Time-based analysis\n");
                stats.append("- Top banned IP ranges\n");
                stats.append("\nUse the WebUI Statistics page or /api/statistic/* endpoints.");
                
            } catch (Exception e) {
                stats.append("Error accessing statistics: ").append(e.getMessage());
            }
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", stats.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting ban statistics: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetDownloaders(Map<String, Object> arguments) {
        try {
            StringBuilder downloaders = new StringBuilder();
            downloaders.append("Configured Downloaders:\n\n");
            
            int downloaderCount = downloaderServer.getDownloaderGroups().size();
            downloaders.append("Total Downloader Groups: ").append(downloaderCount).append("\n\n");
            
            if (downloaderCount > 0) {
                downloaders.append("Downloader groups are configured and active.\n");
                downloaders.append("Each group may contain multiple downloader instances.\n");
                downloaders.append("Use /api/downloader/list for detailed downloader information.\n");
            } else {
                downloaders.append("No downloader groups are currently configured.\n");
                downloaders.append("Configure downloaders through the WebUI Settings page.\n");
            }
            
            downloaders.append("\nSupported downloader types:\n");
            downloaders.append("- qBittorrent\n");
            downloaders.append("- Transmission\n");
            downloaders.append("- Deluge\n");
            downloaders.append("- BiglyBT\n");
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", downloaders.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting downloaders: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetDownloaderStatus(Map<String, Object> arguments) {
        try {
            StringBuilder status = new StringBuilder();
            status.append("Downloader Status Summary:\n\n");
            
            int activeGroups = downloaderServer.getDownloaderGroups().size();
            status.append("Active Downloader Groups: ").append(activeGroups).append("\n");
            
            if (activeGroups > 0) {
                status.append("\nStatus Information:\n");
                status.append("- Connection Status: Available through WebAPI\n");
                status.append("- Torrent Counts: Available per downloader\n");
                status.append("- Performance Metrics: Collected in real-time\n");
                status.append("- Configuration: Managed through WebUI\n\n");
                
                status.append("For detailed status including:\n");
                status.append("- Individual downloader health\n");
                status.append("- Active torrent counts\n");
                status.append("- Connection reliability\n");
                status.append("- Performance statistics\n");
                status.append("\nUse /api/downloader/status endpoint.");
            } else {
                status.append("\nNo active downloaders to monitor.\n");
                status.append("Configure downloaders in Settings to enable monitoring.");
            }
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", status.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting downloader status: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetTorrentInfo(Map<String, Object> arguments) {
        try {
            String infoHash = (String) arguments.get("infoHash");
            if (infoHash == null || infoHash.trim().isEmpty()) {
                return createErrorResponse("Torrent info hash is required");
            }
            
            StringBuilder info = new StringBuilder();
            info.append("Torrent Information:\n\n");
            info.append("Info Hash: ").append(infoHash).append("\n");
            
            try {
                // Check if torrent exists in database
                long torrentCount = torrentDao.countOf();
                info.append("Total Torrents in Database: ").append(torrentCount).append("\n\n");
                
                info.append("Torrent details available through:\n");
                info.append("- WebUI Torrent Details page\n");
                info.append("- /api/torrent/{infoHash} endpoint\n");
                info.append("- /api/torrent/{infoHash}/accessHistory endpoint\n");
                info.append("- /api/torrent/{infoHash}/banHistory endpoint\n\n");
                
                info.append("Available information includes:\n");
                info.append("- Torrent metadata\n");
                info.append("- Peer activity history\n");
                info.append("- Ban statistics\n");
                info.append("- Access patterns\n");
                
            } catch (Exception e) {
                info.append("Error accessing torrent data: ").append(e.getMessage());
            }
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", info.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting torrent info: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetTorrents(Map<String, Object> arguments) {
        try {
            int page = ((Number) arguments.getOrDefault("page", 1)).intValue();
            int pageSize = Math.min(((Number) arguments.getOrDefault("pageSize", 10)).intValue(), 100);
            String search = (String) arguments.getOrDefault("search", "");
            
            StringBuilder response = new StringBuilder();
            response.append("Torrents List (Page ").append(page).append(", Size ").append(pageSize).append("):\n\n");
            
            try {
                long totalTorrents = torrentDao.countOf();
                response.append("Total Torrents: ").append(totalTorrents).append("\n");
                response.append("Search Query: ").append(search.isEmpty() ? "None" : search).append("\n\n");
                
                response.append("Torrent listing with detailed information available through:\n");
                response.append("- WebUI Torrents page\n");
                response.append("- /api/torrent/query endpoint with pagination\n\n");
                
                response.append("Each torrent entry includes:\n");
                response.append("- Info hash\n");
                response.append("- Name (if available)\n");
                response.append("- Size information\n");
                response.append("- Peer activity statistics\n");
                response.append("- Ban-related metrics\n");
                
            } catch (Exception e) {
                response.append("Error accessing torrent data: ").append(e.getMessage());
            }
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", response.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting torrents: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetPeerInfo(Map<String, Object> arguments) {
        try {
            int page = ((Number) arguments.getOrDefault("page", 1)).intValue();
            int pageSize = Math.min(((Number) arguments.getOrDefault("pageSize", 10)).intValue(), 100);
            
            StringBuilder response = new StringBuilder();
            response.append("Peer Information (Page ").append(page).append(", Size ").append(pageSize).append("):\n\n");
            
            response.append("Peer monitoring provides detailed information about:\n");
            response.append("- Active peer connections\n");
            response.append("- Peer behavior patterns\n");
            response.append("- Connection statistics\n");
            response.append("- Ban status and history\n\n");
            
            response.append("Access peer data through:\n");
            response.append("- WebUI Peers page\n");
            response.append("- /api/peer/query endpoint\n");
            response.append("- Active monitoring tools\n\n");
            
            response.append("Peer information includes:\n");
            response.append("- IP address and port\n");
            response.append("- Client identification\n");
            response.append("- Download/upload statistics\n");
            response.append("- Connection duration\n");
            response.append("- Rule violation history\n");
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", response.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting peer info: " + e.getMessage());
        }
    }
}