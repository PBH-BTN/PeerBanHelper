package com.ghostchu.peerbanhelper.module.impl.ai.mcp;

import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Model Context Protocol (MCP) Controller
 * 提供 MCP 服务器功能，支持通过 HTTP 接口与 MCP 客户端通信
 * 
 * This refactored version uses tool providers for better organization and maintainability
 */
@Component
@Slf4j
public final class MCPControllerRefactored extends AbstractFeatureModule {

    private final JavalinWebContainer javalinWebContainer;
    private final MCPToolsRegistry toolsRegistry;
    
    // Tool providers for different functional areas
    @Autowired
    private MCPSystemTools systemTools;
    @Autowired
    private MCPRuleTools ruleTools;
    @Autowired
    private MCPMonitoringTools monitoringTools;
    @Autowired
    private MCPWebAPITools webAPITools;
    @Autowired
    private MCPOperationsTools operationsTools;

    public MCPControllerRefactored(JavalinWebContainer javalinWebContainer, MCPToolsRegistry toolsRegistry) {
        super();
        this.javalinWebContainer = javalinWebContainer;
        this.toolsRegistry = toolsRegistry;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - MCP (Model Context Protocol) - Refactored";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-mcp-refactored";
    }

    @Override
    public void onEnable() {
        try {
            // 初始化并注册所有工具
            initializeTools();
            // 注册 MCP 相关的 HTTP 路由
            registerMCPRoutes();
            log.info("MCP Controller (Refactored) enabled successfully with {} tools", toolsRegistry.getToolCount());
        } catch (Exception e) {
            log.error("Failed to enable MCP Controller (Refactored)", e);
        }
    }

    @Override
    public void onDisable() {
        log.info("MCP Controller (Refactored) disabled");
    }

    /**
     * 初始化并注册所有MCP工具
     */
    private void initializeTools() {
        // Don't clear existing tools - just register new ones
        int initialToolCount = toolsRegistry.getToolCount();

        // 注册各个功能域的工具
        systemTools.registerTools(toolsRegistry);
        ruleTools.registerTools(toolsRegistry);
        monitoringTools.registerTools(toolsRegistry);
        webAPITools.registerTools(toolsRegistry);
        operationsTools.registerTools(toolsRegistry);

        int newToolsAdded = toolsRegistry.getToolCount() - initialToolCount;
        log.info("Added {} new MCP tools from {} providers (total: {})", 
                newToolsAdded, 5, toolsRegistry.getToolCount());
    }

    /**
     * 注册 MCP 相关的 HTTP 路由
     */
    private void registerMCPRoutes() {
        // MCP 扩展端点 - 处理新的工具请求
        javalinWebContainer.javalin()
                .post("/api/mcp/v2", this::handleMCPRequest, Role.ANYONE)
                .get("/api/mcp/v2", this::handleMCPInfo, Role.ANYONE);

        // 扩展兼容性端点
        javalinWebContainer.javalin()
                .get("/api/mcp/v2/info", this::handleMCPInfo, Role.ANYONE)
                .get("/api/mcp/v2/tools", this::handleListTools, Role.ANYONE)
                .post("/api/mcp/v2/tools/invoke", this::handleInvokeTool, Role.ANYONE);

        log.info("MCP v2 HTTP routes registered");
    }

    /**
     * 处理 MCP JSON-RPC 请求
     */
    private void handleMCPRequest(Context ctx) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> request = ctx.bodyAsClass(Map.class);

            String method = (String) request.get("method");
            Object params = request.get("params");
            Object id = request.get("id");

            Map<String, Object> response = new java.util.HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id);

            if (method == null) {
                response.put("error", Map.of(
                        "code", -32600,
                        "message", "Invalid Request"
                ));
                ctx.json(response);
                return;
            }

            try {
                Object result = switch (method) {
                    case "initialize" -> handleInitializeRPC(params);
                    case "tools/list" -> handleListToolsRPC(params);
                    case "tools/call" -> handleCallToolRPC(params);
                    default -> throw new RuntimeException("Method not found: " + method);
                };

                response.put("result", result);
            } catch (Exception e) {
                response.put("error", Map.of(
                        "code", -32603,
                        "message", "Internal error",
                        "data", e.getMessage()
                ));
                log.error("Error processing MCP method: " + method, e);
            }

            ctx.json(response);

        } catch (Exception e) {
            log.error("Error handling MCP request", e);
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(Map.of(
                    "jsonrpc", "2.0",
                    "error", Map.of(
                            "code", -32700,
                            "message", "Parse error"
                    ),
                    "id", null
            ));
        }
    }

    /**
     * 处理初始化请求
     */
    private Object handleInitializeRPC(Object params) {
        return Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(
                        "tools", Map.of()
                ),
                "serverInfo", Map.of(
                        "name", "PeerBanHelper MCP Server v2",
                        "version", "2.0.0"
                )
        );
    }

    /**
     * 处理工具列表请求
     */
    private Object handleListToolsRPC(Object params) {
        return Map.of(
                "tools", toolsRegistry.getAllTools()
        );
    }

    /**
     * 处理工具调用请求
     */
    private Object handleCallToolRPC(Object params) {
        if (!(params instanceof Map)) {
            throw new RuntimeException("Invalid parameters");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> paramsMap = (Map<String, Object>) params;
        String name = (String) paramsMap.get("name");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) paramsMap.getOrDefault("arguments", Map.of());

        if (name == null) {
            throw new RuntimeException("Tool name is required");
        }

        return toolsRegistry.invokeTool(name, arguments)
                .orElseThrow(() -> new RuntimeException("Tool not found: " + name));
    }

    /**
     * 处理 MCP 信息请求
     */
    private void handleMCPInfo(Context ctx) {
        Map<String, Object> info = Map.of(
                "name", "PeerBanHelper MCP Server v2",
                "version", "2.0.0",
                "description", "Enhanced Model Context Protocol server for PeerBanHelper with modular tool providers",
                "tools", toolsRegistry.getToolCount(),
                "providers", List.of(
                        systemTools.getProviderName(),
                        ruleTools.getProviderName(),
                        monitoringTools.getProviderName(),
                        webAPITools.getProviderName(),
                        operationsTools.getProviderName()
                ),
                "features", List.of(
                        "Rule module management",
                        "System monitoring",
                        "WebAPI integration",
                        "Background task monitoring",
                        "Module-specific tools"
                )
        );
        ctx.json(new com.ghostchu.peerbanhelper.web.wrapper.StdResp(true, null, info));
    }

    /**
     * 处理工具列表请求 (兼容性端点)
     */
    private void handleListTools(Context ctx) {
        List<McpSchema.Tool> tools = toolsRegistry.getAllTools();
        ctx.json(new com.ghostchu.peerbanhelper.web.wrapper.StdResp(true, null, Map.of(
                "tools", tools,
                "count", tools.size(),
                "providers", List.of(
                        systemTools.getProviderName(),
                        ruleTools.getProviderName(),
                        monitoringTools.getProviderName(),
                        webAPITools.getProviderName(),
                        operationsTools.getProviderName()
                )
        )));
    }

    /**
     * 处理工具调用请求 (兼容性端点)
     */
    private void handleInvokeTool(Context ctx) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> request = ctx.bodyAsClass(Map.class);
            String toolName = (String) request.get("name");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) request.getOrDefault("arguments", Map.of());

            if (toolName == null) {
                ctx.json(new com.ghostchu.peerbanhelper.web.wrapper.StdResp(false, "Tool name is required", null));
                return;
            }

            Map<String, Object> result = toolsRegistry.invokeTool(toolName, arguments)
                    .orElse(Map.of("error", "Tool not found: " + toolName));

            ctx.json(new com.ghostchu.peerbanhelper.web.wrapper.StdResp(true, null, result));
        } catch (Exception e) {
            log.error("Error invoking tool", e);
            ctx.json(new com.ghostchu.peerbanhelper.web.wrapper.StdResp(false, "Error invoking tool: " + e.getMessage(), null));
        }
    }
}