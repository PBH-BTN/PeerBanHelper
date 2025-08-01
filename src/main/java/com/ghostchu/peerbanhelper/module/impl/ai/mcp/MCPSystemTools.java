package com.ghostchu.peerbanhelper.module.impl.ai.mcp;

import com.ghostchu.peerbanhelper.BuildMeta;
import com.ghostchu.peerbanhelper.DownloaderServer;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.database.dao.impl.HistoryDao;
import com.ghostchu.peerbanhelper.module.FeatureModule;
import com.ghostchu.peerbanhelper.module.ModuleManagerImpl;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP tool provider for system status and metadata operations
 */
@Slf4j
@Component
public class MCPSystemTools implements MCPToolProvider {
    
    @Autowired
    private DownloaderServer downloaderServer;
    @Autowired
    private ModuleManagerImpl moduleManager;
    @Autowired
    private BuildMeta buildMeta;
    @Autowired
    private HistoryDao historyDao;

    @Override
    public void registerTools(MCPToolsRegistry registry) {
        log.debug("Registering system tools for MCP");
        
        McpSchema.JsonSchema noInput = new McpSchema.JsonSchema(
                "object", // type
                Map.of(), // properties
                List.of(), // required
                null, // additionalProperties
                Map.of(), // definitions
                Map.of()  // patternProperties
        );

        // System Status and Metadata Tools
        registry.registerTool("get_pbh_status", "Get PeerBanHelper current status and system information", noInput, this::handleGetStatusTool);
        registry.registerTool("get_pbh_config", "Get PeerBanHelper configuration information", noInput, this::handleGetConfigTool);
        registry.registerTool("get_pbh_metadata", "Get PeerBanHelper metadata including version and modules", noInput, this::handleGetMetadataTool);
        registry.registerTool("get_system_metrics", "Get system performance metrics including JVM memory and thread info", noInput, this::handleGetSystemMetricsTool);
        registry.registerTool("get_module_status", "Get status of all loaded modules", noInput, this::handleGetModuleStatusTool);
    }
    
    @Override
    public String getProviderName() {
        return "SystemTools";
    }

    private Map<String, Object> handleGetStatusTool(Map<String, Object> arguments) {
        try {
            long bannedPeersCount = historyDao.queryForEq("ban", true).size();
            Map<String, Object> statusData = Map.of(
                    "online", true,
                    "version", buildMeta.getVersion(),
                    "commit", buildMeta.getCommitId(),
                    "uptime", System.currentTimeMillis() - Main.getStartupTime(),
                    "bannedPeersCount", bannedPeersCount,
                    "activeDownloaders", downloaderServer.getDownloaderGroups().size(),
                    "loadedModules", moduleManager.getModules().size()
            );
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", "PeerBanHelper Status:\n" +
                            "- Online: " + statusData.get("online") + "\n" +
                            "- Version: " + statusData.get("version") + "\n" +
                            "- Commit: " + statusData.get("commit") + "\n" +
                            "- Uptime: " + statusData.get("uptime") + " ms\n" +
                            "- Banned Peers: " + statusData.get("bannedPeersCount") + "\n" +
                            "- Active Downloaders: " + statusData.get("activeDownloaders") + "\n" +
                            "- Loaded Modules: " + statusData.get("loadedModules")
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting status: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetConfigTool(Map<String, Object> arguments) {
        try {
            Map<String, Object> configData = Map.of(
                    "installationId", Main.getMainConfig().getString("installation-id", "not-initialized"),
                    "modulesCount", moduleManager.getModules().size(),
                    "webContainerPort", Main.getMainConfig().getInt("server.http", 0)
            );
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", "PeerBanHelper Configuration:\n" +
                            "- Installation ID: " + configData.get("installationId") + "\n" +
                            "- Modules Count: " + configData.get("modulesCount") + "\n" +
                            "- Web Port: " + configData.get("webContainerPort")
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting config: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetMetadataTool(Map<String, Object> arguments) {
        try {
            StringBuilder modulesList = new StringBuilder();
            for (FeatureModule module : moduleManager.getModules()) {
                modulesList.append("- ").append(module.getName())
                        .append(" (").append(module.getConfigName()).append("): ")
                        .append(module.isEnabled() ? "Enabled" : "Disabled")
                        .append("\n");
            }
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", "PeerBanHelper Metadata:\n" +
                            "Version: " + buildMeta.getVersion() + "\n" +
                            "Build ID: " + buildMeta.getBuildId() + "\n" +
                            "Commit: " + buildMeta.getCommitId() + "\n" +
                            "Branch: " + buildMeta.getBranch() + "\n" +
                            "Build Time: " + buildMeta.getBuildTimestamp() + "\n" +
                            "Installation ID: " + Main.getMainConfig().getString("installation-id", "not-initialized") + "\n\n" +
                            "Loaded Modules:\n" + modulesList.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting metadata: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetSystemMetricsTool(Map<String, Object> arguments) {
        try {
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", "System Metrics:\n" +
                            "Memory Usage:\n" +
                            "- Used: " + (usedMemory / 1024 / 1024) + " MB\n" +
                            "- Free: " + (freeMemory / 1024 / 1024) + " MB\n" +
                            "- Total: " + (totalMemory / 1024 / 1024) + " MB\n" +
                            "- Max: " + (maxMemory / 1024 / 1024) + " MB\n" +
                            "Threads:\n" +
                            "- Active: " + Thread.activeCount() + "\n" +
                            "Processors: " + runtime.availableProcessors()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting system metrics: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetModuleStatusTool(Map<String, Object> arguments) {
        try {
            StringBuilder moduleStatus = new StringBuilder();
            moduleStatus.append("Module Status Summary:\n");
            moduleStatus.append("Total modules: ").append(moduleManager.getModules().size()).append("\n\n");
            
            int enabledCount = 0;
            for (FeatureModule module : moduleManager.getModules()) {
                boolean isEnabled = module.isEnabled();
                if (isEnabled) enabledCount++;
                
                moduleStatus.append("• ").append(module.getName()).append("\n");
                moduleStatus.append("  Config: ").append(module.getConfigName()).append("\n");
                moduleStatus.append("  Status: ").append(isEnabled ? "✓ Enabled" : "✗ Disabled").append("\n");
                moduleStatus.append("  Configurable: ").append(module.isConfigurable() ? "Yes" : "No").append("\n\n");
            }
            
            moduleStatus.append("Summary: ").append(enabledCount).append(" enabled, ")
                       .append(moduleManager.getModules().size() - enabledCount).append(" disabled");
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", moduleStatus.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting module status: " + e.getMessage());
        }
    }
}