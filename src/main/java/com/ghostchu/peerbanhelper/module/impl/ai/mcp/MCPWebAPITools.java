package com.ghostchu.peerbanhelper.module.impl.ai.mcp;

import com.ghostchu.peerbanhelper.module.FeatureModule;
import com.ghostchu.peerbanhelper.module.ModuleManagerImpl;
import com.ghostchu.peerbanhelper.module.impl.webapi.*;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP tool provider for WebAPI module integration and endpoint information
 */
@Slf4j
@Component
public class MCPWebAPITools implements MCPToolProvider {
    
    @Autowired
    private ModuleManagerImpl moduleManager;

    @Override
    public void registerTools(MCPToolsRegistry registry) {
        log.debug("Registering WebAPI integration tools for MCP");
        
        McpSchema.JsonSchema noInput = new McpSchema.JsonSchema(
                "object",
                Map.of(),
                List.of(),
                null,
                Map.of(),
                Map.of()
        );
        
        McpSchema.JsonSchema moduleNameSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "moduleName", Map.of("type", "string", "description", "Name of the WebAPI module")
                ),
                List.of("moduleName"),
                null,
                Map.of(),
                Map.of()
        );

        // WebAPI Integration Tools
        registry.registerTool("get_webapi_modules", "Get list of all WebAPI modules and their endpoints", noInput, this::handleGetWebAPIModules);
        registry.registerTool("get_webapi_module_info", "Get detailed information about a specific WebAPI module", moduleNameSchema, this::handleGetWebAPIModuleInfo);
        registry.registerTool("get_webapi_endpoints", "Get comprehensive list of available WebAPI endpoints", noInput, this::handleGetWebAPIEndpoints);
        registry.registerTool("get_webapi_authentication", "Get information about WebAPI authentication and roles", noInput, this::handleGetWebAPIAuthentication);
    }
    
    @Override
    public String getProviderName() {
        return "WebAPITools";
    }

    private Map<String, Object> handleGetWebAPIModules(Map<String, Object> arguments) {
        try {
            StringBuilder webApiModules = new StringBuilder();
            webApiModules.append("WebAPI Modules Overview:\n\n");
            
            int totalWebAPIModules = 0;
            int activeWebAPIModules = 0;
            
            for (FeatureModule module : moduleManager.getModules()) {
                String className = module.getClass().getSimpleName();
                if (className.contains("Controller") && module.getClass().getPackage().getName().contains("webapi")) {
                    totalWebAPIModules++;
                    boolean isEnabled = module.isEnabled();
                    if (isEnabled) activeWebAPIModules++;
                    
                    webApiModules.append("• ").append(module.getName()).append("\n");
                    webApiModules.append("  Class: ").append(className).append("\n");
                    webApiModules.append("  Config: ").append(module.getConfigName()).append("\n");
                    webApiModules.append("  Status: ").append(isEnabled ? "✓ Active" : "✗ Inactive").append("\n");
                    webApiModules.append("  Category: ").append(getWebAPICategory(className)).append("\n\n");
                }
            }
            
            webApiModules.append("Summary: ").append(activeWebAPIModules).append(" active, ")
                         .append(totalWebAPIModules - activeWebAPIModules).append(" inactive (")
                         .append(totalWebAPIModules).append(" total WebAPI modules)");
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", webApiModules.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting WebAPI modules: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetWebAPIModuleInfo(Map<String, Object> arguments) {
        try {
            String moduleName = (String) arguments.get("moduleName");
            if (moduleName == null) {
                return createErrorResponse("Module name is required");
            }
            
            Optional<FeatureModule> moduleOpt = moduleManager.getModules().stream()
                    .filter(m -> (m.getName().equals(moduleName) || m.getConfigName().equals(moduleName)) &&
                               m.getClass().getPackage().getName().contains("webapi"))
                    .findFirst();
            
            if (moduleOpt.isEmpty()) {
                return createErrorResponse("WebAPI module not found: " + moduleName);
            }
            
            FeatureModule module = moduleOpt.get();
            StringBuilder info = new StringBuilder();
            info.append("WebAPI Module Information:\n\n");
            info.append("Name: ").append(module.getName()).append("\n");
            info.append("Config Name: ").append(module.getConfigName()).append("\n");
            info.append("Class: ").append(module.getClass().getSimpleName()).append("\n");
            info.append("Status: ").append(module.isEnabled() ? "✓ Enabled" : "✗ Disabled").append("\n");
            info.append("Configurable: ").append(module.isConfigurable() ? "Yes" : "No").append("\n");
            info.append("Category: ").append(getWebAPICategory(module.getClass().getSimpleName())).append("\n\n");
            
            // Add specific endpoint information based on module type
            info.append("Expected Endpoints:\n");
            info.append(getModuleEndpoints(module)).append("\n");
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", info.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting WebAPI module info: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetWebAPIEndpoints(Map<String, Object> arguments) {
        try {
            StringBuilder endpoints = new StringBuilder();
            endpoints.append("WebAPI Endpoints Overview:\n\n");
            
            endpoints.append("Core System Endpoints:\n");
            endpoints.append("- GET /api/metadata/manifest - System metadata and build information\n");
            endpoints.append("- GET /api/statistic/counter - Basic system counters\n");
            endpoints.append("- GET /api/statistic/analysis/field - Historical data analysis\n\n");
            
            endpoints.append("Authentication & Security:\n");
            endpoints.append("- POST /api/auth/login - User authentication\n");
            endpoints.append("- POST /api/auth/logout - User logout\n");
            endpoints.append("- GET /api/auth/status - Authentication status\n\n");
            
            endpoints.append("Ban Management:\n");
            endpoints.append("- GET /api/ban/list - Get ban list with pagination\n");
            endpoints.append("- POST /api/ban/unban - Unban specific IPs\n");
            endpoints.append("- GET /api/ban/logs - Ban operation logs\n");
            endpoints.append("- GET /api/ban/statistics - Ban statistics\n\n");
            
            endpoints.append("Downloader Management:\n");
            endpoints.append("- GET /api/downloader/list - List configured downloaders\n");
            endpoints.append("- GET /api/downloader/status - Downloader status information\n\n");
            
            endpoints.append("Torrent & Peer Management:\n");
            endpoints.append("- GET /api/torrent/query - Query torrents with pagination\n");
            endpoints.append("- GET /api/torrent/{infoHash} - Get specific torrent information\n");
            endpoints.append("- GET /api/torrent/{infoHash}/accessHistory - Torrent access history\n");
            endpoints.append("- GET /api/torrent/{infoHash}/banHistory - Torrent ban history\n");
            endpoints.append("- GET /api/peer/query - Query peer information\n\n");
            
            endpoints.append("Monitoring & Logs:\n");
            endpoints.append("- GET /api/logs/history - System logs and history\n");
            endpoints.append("- GET /api/monitoring/active - Active monitoring data\n");
            endpoints.append("- GET /api/monitoring/swarm - Swarm tracking information\n\n");
            
            endpoints.append("Configuration & Management:\n");
            endpoints.append("- GET /api/config/modules - Module configuration\n");
            endpoints.append("- POST /api/config/reload - Reload configuration\n");
            endpoints.append("- GET /api/rules/subscriptions - Rule subscriptions\n\n");
            
            endpoints.append("Note: All endpoints require appropriate authentication and role permissions.");
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", endpoints.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting WebAPI endpoints: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetWebAPIAuthentication(Map<String, Object> arguments) {
        try {
            StringBuilder authInfo = new StringBuilder();
            authInfo.append("WebAPI Authentication Information:\n\n");
            
            authInfo.append("Authentication Methods:\n");
            authInfo.append("- Session-based authentication\n");
            authInfo.append("- Role-based access control (RBAC)\n");
            authInfo.append("- Token-based authentication for API access\n\n");
            
            authInfo.append("Available Roles:\n");
            authInfo.append("- ANYONE: Public access (limited endpoints)\n");
            authInfo.append("- USER_READ: Read-only user access\n");
            authInfo.append("- USER_WRITE: Read-write user access\n");
            authInfo.append("- ADMIN: Administrative access\n");
            authInfo.append("- PBH_PLUS: PeerBanHelper Plus features\n\n");
            
            authInfo.append("Authentication Endpoints:\n");
            authInfo.append("- POST /api/auth/login - User login\n");
            authInfo.append("- POST /api/auth/logout - User logout\n");
            authInfo.append("- GET /api/auth/status - Check authentication status\n");
            authInfo.append("- GET /api/auth/user - Get current user information\n\n");
            
            authInfo.append("Security Features:\n");
            authInfo.append("- CSRF protection\n");
            authInfo.append("- Session management\n");
            authInfo.append("- Rate limiting\n");
            authInfo.append("- Input validation\n");
            authInfo.append("- SQL injection prevention\n\n");
            
            authInfo.append("MCP Integration:\n");
            authInfo.append("- MCP endpoints available at /api/mcp\n");
            authInfo.append("- JSON-RPC 2.0 protocol support\n");
            authInfo.append("- Tool-based access control\n");
            authInfo.append("- Standardized error responses\n");
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", authInfo.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting WebAPI authentication info: " + e.getMessage());
        }
    }

    private String getWebAPICategory(String className) {
        if (className.contains("Ban")) return "Ban Management";
        if (className.contains("Downloader")) return "Downloader Management";
        if (className.contains("Torrent")) return "Torrent Management";
        if (className.contains("Peer")) return "Peer Management";
        if (className.contains("Auth")) return "Authentication";
        if (className.contains("Metadata")) return "System Information";
        if (className.contains("Metrics")) return "System Metrics";
        if (className.contains("Logs")) return "Logging";
        if (className.contains("Chart")) return "Data Visualization";
        if (className.contains("General")) return "General API";
        if (className.contains("BackgroundTask")) return "Background Tasks";
        if (className.contains("Alert")) return "Alerting";
        if (className.contains("Push")) return "Push Notifications";
        if (className.contains("Plus")) return "PBH Plus Features";
        if (className.contains("Utilities")) return "Utility Functions";
        if (className.contains("OOBE")) return "Setup & Configuration";
        if (className.contains("Lab")) return "Experimental Features";
        if (className.contains("AutoStun")) return "Network Testing";
        if (className.contains("EasterEgg")) return "Easter Eggs";
        return "Other";
    }

    private String getModuleEndpoints(FeatureModule module) {
        String className = module.getClass().getSimpleName();
        
        switch (className) {
            case "PBHBanController":
                return "- GET /api/ban/list\n- POST /api/ban/unban\n- GET /api/ban/logs\n- GET /api/ban/statistics";
            case "PBHDownloaderController":
                return "- GET /api/downloader/list\n- GET /api/downloader/status\n- POST /api/downloader/test";
            case "PBHTorrentController":
                return "- GET /api/torrent/query\n- GET /api/torrent/{hash}\n- GET /api/torrent/{hash}/accessHistory";
            case "PBHPeerController":
                return "- GET /api/peer/query\n- GET /api/peer/{id}\n- GET /api/peer/statistics";
            case "PBHMetadataController":
                return "- GET /api/metadata/manifest";
            case "PBHMetricsController":
                return "- GET /api/statistic/counter\n- GET /api/statistic/analysis/field";
            case "PBHLogsController":
                return "- GET /api/logs/history";
            case "PBHAuthenticateController":
                return "- POST /api/auth/login\n- POST /api/auth/logout\n- GET /api/auth/status";
            case "PBHGeneralController":
                return "- GET /api/general/info\n- GET /api/general/health";
            case "PBHBackgroundTaskController":
                return "- GET /api/tasks/list\n- GET /api/tasks/status";
            default:
                return "- Module-specific endpoints (see WebAPI documentation)";
        }
    }
}