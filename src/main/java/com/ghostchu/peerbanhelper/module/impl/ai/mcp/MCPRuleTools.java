package com.ghostchu.peerbanhelper.module.impl.ai.mcp;

import com.ghostchu.peerbanhelper.module.FeatureModule;
import com.ghostchu.peerbanhelper.module.ModuleManagerImpl;
import com.ghostchu.peerbanhelper.module.RuleFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.rule.*;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP tool provider for rule module management and configuration
 */
@Slf4j
@Component
public class MCPRuleTools implements MCPToolProvider {
    
    @Autowired
    private ModuleManagerImpl moduleManager;

    @Override
    public void registerTools(MCPToolsRegistry registry) {
        log.debug("Registering rule management tools for MCP");
        
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
                        "moduleName", Map.of("type", "string", "description", "Name of the rule module")
                ),
                List.of("moduleName"),
                null,
                Map.of(),
                Map.of()
        );
        
        McpSchema.JsonSchema moduleToggleSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "moduleName", Map.of("type", "string", "description", "Name of the rule module"),
                        "enabled", Map.of("type", "boolean", "description", "Whether to enable or disable the module")
                ),
                List.of("moduleName", "enabled"),
                null,
                Map.of(),
                Map.of()
        );

        // Rule Management Tools
        registry.registerTool("get_rule_modules", "Get list of all rule modules and their status", noInput, this::handleGetRuleModules);
        registry.registerTool("get_rule_module_info", "Get detailed information about a specific rule module", moduleNameSchema, this::handleGetRuleModuleInfo);
        registry.registerTool("toggle_rule_module", "Enable or disable a rule module", moduleToggleSchema, this::handleToggleRuleModule);
        registry.registerTool("get_rule_statistics", "Get statistics for all rule modules", noInput, this::handleGetRuleStatistics);
        registry.registerTool("get_expression_rules", "Get list of custom expression rules", noInput, this::handleGetExpressionRules);
    }
    
    @Override
    public String getProviderName() {
        return "RuleTools";
    }

    private Map<String, Object> handleGetRuleModules(Map<String, Object> arguments) {
        try {
            StringBuilder ruleModules = new StringBuilder();
            ruleModules.append("Rule Modules Overview:\n\n");
            
            int totalRules = 0;
            int activeRules = 0;
            
            for (FeatureModule module : moduleManager.getModules()) {
                if (module instanceof RuleFeatureModule) {
                    totalRules++;
                    boolean isEnabled = module.isEnabled();
                    if (isEnabled) activeRules++;
                    
                    ruleModules.append("• ").append(module.getName()).append("\n");
                    ruleModules.append("  Config: ").append(module.getConfigName()).append("\n");
                    ruleModules.append("  Status: ").append(isEnabled ? "✓ Active" : "✗ Inactive").append("\n");
                    ruleModules.append("  Type: ").append(module.getClass().getSimpleName()).append("\n\n");
                }
            }
            
            ruleModules.append("Summary: ").append(activeRules).append(" active, ")
                      .append(totalRules - activeRules).append(" inactive (")
                      .append(totalRules).append(" total rules)");
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", ruleModules.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting rule modules: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetRuleModuleInfo(Map<String, Object> arguments) {
        try {
            String moduleName = (String) arguments.get("moduleName");
            if (moduleName == null) {
                return createErrorResponse("Module name is required");
            }
            
            Optional<FeatureModule> moduleOpt = moduleManager.getModules().stream()
                    .filter(m -> m.getName().equals(moduleName) || m.getConfigName().equals(moduleName))
                    .findFirst();
            
            if (moduleOpt.isEmpty()) {
                return createErrorResponse("Rule module not found: " + moduleName);
            }
            
            FeatureModule module = moduleOpt.get();
            if (!(module instanceof RuleFeatureModule)) {
                return createErrorResponse("Module is not a rule module: " + moduleName);
            }
            
            StringBuilder info = new StringBuilder();
            info.append("Rule Module Information:\n\n");
            info.append("Name: ").append(module.getName()).append("\n");
            info.append("Config Name: ").append(module.getConfigName()).append("\n");
            info.append("Class: ").append(module.getClass().getSimpleName()).append("\n");
            info.append("Status: ").append(module.isEnabled() ? "✓ Enabled" : "✗ Disabled").append("\n");
            info.append("Configurable: ").append(module.isConfigurable() ? "Yes" : "No").append("\n");
            
            // Add specific information for known rule types
            if (module instanceof ExpressionRule) {
                info.append("Type: Custom Expression Rules\n");
                info.append("Description: Allows custom JavaScript/Aviator expressions for peer filtering\n");
            } else if (module instanceof ProgressCheatBlocker) {
                info.append("Type: Progress Cheat Blocker\n");
                info.append("Description: Detects and blocks peers with suspicious download progress\n");
            } else if (module instanceof AutoRangeBan) {
                info.append("Type: Auto Range Ban\n");
                info.append("Description: Automatically bans IP ranges based on patterns\n");
            } else if (module instanceof ClientNameBlacklist) {
                info.append("Type: Client Name Blacklist\n");
                info.append("Description: Blocks peers based on client software names\n");
            } else if (module instanceof PeerIdBlacklist) {
                info.append("Type: Peer ID Blacklist\n");
                info.append("Description: Blocks peers based on peer ID patterns\n");
            } else if (module instanceof IPBlackList || module instanceof IPBlackRuleList) {
                info.append("Type: IP Blacklist\n");
                info.append("Description: Blocks specific IP addresses or ranges\n");
            } else if (module instanceof MultiDialingBlocker) {
                info.append("Type: Multi-Dialing Blocker\n");
                info.append("Description: Prevents peers from making multiple connections\n");
            } else if (module instanceof PTRBlacklist) {
                info.append("Type: PTR Blacklist\n");
                info.append("Description: Blocks peers based on reverse DNS (PTR) records\n");
            } else if (module instanceof BtnNetworkOnline) {
                info.append("Type: BTN Network Online\n");
                info.append("Description: BTN network integration for peer verification\n");
            }
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", info.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting rule module info: " + e.getMessage());
        }
    }

    private Map<String, Object> handleToggleRuleModule(Map<String, Object> arguments) {
        try {
            String moduleName = (String) arguments.get("moduleName");
            Boolean enabled = (Boolean) arguments.get("enabled");
            
            if (moduleName == null || enabled == null) {
                return createErrorResponse("Module name and enabled status are required");
            }
            
            Optional<FeatureModule> moduleOpt = moduleManager.getModules().stream()
                    .filter(m -> m.getName().equals(moduleName) || m.getConfigName().equals(moduleName))
                    .findFirst();
            
            if (moduleOpt.isEmpty()) {
                return createErrorResponse("Rule module not found: " + moduleName);
            }
            
            FeatureModule module = moduleOpt.get();
            if (!(module instanceof RuleFeatureModule)) {
                return createErrorResponse("Module is not a rule module: " + moduleName);
            }
            
            // This is a read-only preview - actual toggling would require proper permissions and safety checks
            String action = enabled ? "enable" : "disable";
            String currentStatus = module.isEnabled() ? "enabled" : "disabled";
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", "Rule Module Toggle Request:\n" +
                            "Module: " + module.getName() + "\n" +
                            "Current Status: " + currentStatus + "\n" +
                            "Requested Action: " + action + "\n" +
                            "Note: This is a preview. Actual module toggling requires proper authorization and would be performed through the main configuration system."
            )));
        } catch (Exception e) {
            return createErrorResponse("Error toggling rule module: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetRuleStatistics(Map<String, Object> arguments) {
        try {
            StringBuilder stats = new StringBuilder();
            stats.append("Rule Module Statistics:\n\n");
            
            int totalRules = 0;
            int activeRules = 0;
            int configurableRules = 0;
            
            Map<String, Integer> typeCount = new java.util.HashMap<>();
            
            for (FeatureModule module : moduleManager.getModules()) {
                if (module instanceof RuleFeatureModule) {
                    totalRules++;
                    if (module.isEnabled()) activeRules++;
                    if (module.isConfigurable()) configurableRules++;
                    
                    String type = module.getClass().getSimpleName();
                    typeCount.put(type, typeCount.getOrDefault(type, 0) + 1);
                }
            }
            
            stats.append("Overview:\n");
            stats.append("- Total Rules: ").append(totalRules).append("\n");
            stats.append("- Active Rules: ").append(activeRules).append("\n");
            stats.append("- Inactive Rules: ").append(totalRules - activeRules).append("\n");
            stats.append("- Configurable Rules: ").append(configurableRules).append("\n\n");
            
            stats.append("Rule Types:\n");
            typeCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry -> stats.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n"));
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", stats.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting rule statistics: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetExpressionRules(Map<String, Object> arguments) {
        try {
            Optional<FeatureModule> expressionRuleOpt = moduleManager.getModules().stream()
                    .filter(m -> m instanceof ExpressionRule)
                    .findFirst();
            
            if (expressionRuleOpt.isEmpty()) {
                return createSuccessResponse(List.of(Map.of(
                        "type", "text",
                        "text", "Expression Rules module is not loaded or available."
                )));
            }
            
            ExpressionRule expressionRule = (ExpressionRule) expressionRuleOpt.get();
            
            String status = expressionRule.isEnabled() ? "✓ Enabled" : "✗ Disabled";
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", "Expression Rules Module:\n" +
                            "Status: " + status + "\n" +
                            "Type: Custom JavaScript/Aviator expressions\n" +
                            "Description: Allows advanced peer filtering using custom expressions\n" +
                            "Features:\n" +
                            "- Custom JavaScript expressions\n" +
                            "- Aviator expression engine support\n" +
                            "- Access to peer and torrent data\n" +
                            "- Complex filtering logic\n" +
                            "- Script management and validation\n\n" +
                            "Note: Expression management requires access to the ExpressionRule WebAPI endpoints."
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting expression rules: " + e.getMessage());
        }
    }
}