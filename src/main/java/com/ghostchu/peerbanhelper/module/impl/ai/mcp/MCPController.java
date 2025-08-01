package com.ghostchu.peerbanhelper.module.impl.ai.mcp;

import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Model Context Protocol (MCP) Controller
 * 提供 MCP 服务器功能，支持通过 HTTP 接口与 MCP 客户端通信
 */
@Component
@Slf4j
public final class MCPController extends AbstractFeatureModule {

    private final JavalinWebContainer javalinWebContainer;
    private final MCPToolsRegistry toolsRegistry;

    public MCPController(JavalinWebContainer javalinWebContainer, MCPToolsRegistry toolsRegistry) {
        super();
        this.javalinWebContainer = javalinWebContainer;
        this.toolsRegistry = toolsRegistry;
        // 初始化并注册所有工具
        initializeTools();
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - MCP (Model Context Protocol)";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-mcp";
    }

    @Override
    public void onEnable() {
        try {
            // 注册 MCP 相关的 HTTP 路由
            registerMCPRoutes();
            log.info("MCP Controller enabled successfully");
        } catch (Exception e) {
            log.error("Failed to enable MCP Controller", e);
        }
    }

    @Override
    public void onDisable() {
        log.info("MCP Controller disabled");
    }

    /**
     * 初始化并注册所有MCP工具
     */
    private void initializeTools() {
        // 清空现有工具
        toolsRegistry.clearAll();

        McpSchema.JsonSchema noInput = new McpSchema.JsonSchema(
                "object", // type
                Map.of(), // properties
                List.of(), // required
                null, // additionalProperties
                Map.of(), // definitions
                Map.of()  // patternProperties
        );

        toolsRegistry.registerTool("get_pbh_status", "Get PeerBanHelper current status", noInput, this::handleGetStatusTool);
        toolsRegistry.registerTool("get_pbh_config", "Get PeerBanHelper configuration information", noInput, this::handleGetConfigTool);
        toolsRegistry.registerTool("get_peer_statistics", "Get peer ban statistics and counts", noInput, this::handleGetPeerStatisticsTool);

        log.info("Registered {} MCP tools", toolsRegistry.getToolCount());
    }

    /**
     * 注册 MCP 相关的 HTTP 路由
     */
    private void registerMCPRoutes() {
        // MCP 根端点 - 处理所有 MCP 请求
        javalinWebContainer.javalin()
                .post("/api/mcp", this::handleMCPRequest, Role.ANYONE)
                .get("/api/mcp", this::handleMCPInfo, Role.ANYONE);

        // 兼容性端点
        javalinWebContainer.javalin()
                .get("/api/mcp/info", this::handleMCPInfo, Role.ANYONE)
                .get("/api/mcp/tools", this::handleListTools, Role.ANYONE)
                .post("/api/mcp/tools/invoke", this::handleInvokeTool, Role.ANYONE)
                .post("/api/mcp/initialize", this::handleInitialize, Role.ANYONE);

        log.info("MCP HTTP routes registered");
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
                    )
            ));
        }
    }

    /**
     * 处理初始化 RPC 请求
     */
    private Object handleInitializeRPC(Object params) {
        return Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(
                        "tools", Map.of("listChanged", false),
                        "logging", Map.of()
                ),
                "serverInfo", Map.of(
                        "name", "PeerBanHelper",
                        "version", "1.0.0"
                )
        );
    }

    /**
     * 处理工具列表 RPC 请求
     */
    private Object handleListToolsRPC(Object params) {
        List<McpSchema.Tool> tools = toolsRegistry.getAllTools();
        return Map.of("tools", tools);
    }

    /**
     * 处理工具调用 RPC 请求
     */
    @SuppressWarnings("unchecked")
    private Object handleCallToolRPC(Object params) {
        Map<String, Object> toolParams = (Map<String, Object>) params;
        String toolName = (String) toolParams.get("name");
        Map<String, Object> arguments = (Map<String, Object>) toolParams.get("arguments");

        final Map<String, Object> finalArguments = arguments != null ? arguments : Map.of();

        // 使用注册中心查找并执行工具
        return toolsRegistry.getToolHandler(toolName)
                .map(handler -> createMCPToolResult(handler.apply(finalArguments)))
                .orElse(createMCPErrorResult("Unknown tool: " + toolName));
    }

    /**
     * 创建MCP工具调用结果
     */
    private Map<String, Object> createMCPToolResult(Map<String, Object> toolResult) {
        return Map.of(
                "content", toolResult.get("content"),
                "isError", toolResult.get("isError")
        );
    }

    /**
     * 创建MCP错误结果
     */
    private Map<String, Object> createMCPErrorResult(String errorMessage) {
        return Map.of(
                "content", List.of(Map.of(
                        "type", "text",
                        "text", errorMessage
                )),
                "isError", true
        );
    }

    /**
     * 处理获取 MCP 服务器信息的请求
     */
    private void handleMCPInfo(Context ctx) {
        try {
            // 使用统一的工具注册中心，避免硬编码
            List<McpSchema.Tool> tools = toolsRegistry.getAllTools();
            List<Map<String, Object>> availableTools = tools.stream()
                    .map(tool -> Map.<String, Object>of(
                            "name", tool.name(),
                            "description", tool.description()
                    ))
                    .toList();

            Map<String, Object> info = Map.of(
                    "name", "PeerBanHelper",
                    "version", "1.0.0",
                    "description", "PeerBanHelper MCP Server",
                    "mcpVersion", "2024-11-05",
                    "capabilities", Map.of(
                            "tools", Map.of("listChanged", false),
                            "logging", Map.of()
                    ),
                    "availableTools", availableTools
            );

            ctx.json(new StdResp(true, "MCP server info", info));
        } catch (Exception e) {
            log.error("Error handling MCP info request", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(new StdResp(false, "Internal error", null));
        }
    }

    /**
     * 处理获取工具列表的请求
     */
    private void handleListTools(Context ctx) {
        try {
            // 使用注册中心获取工具列表，避免硬编码
            List<McpSchema.Tool> tools = toolsRegistry.getAllTools();
            List<Map<String, Object>> toolsForResponse = tools.stream()
                    .map(tool -> Map.<String, Object>of(
                            "name", tool.name(),
                            "description", tool.description(),
                            "inputSchema", Map.of(
                                    "type", "object",
                                    "properties", Map.of(),
                                    "required", List.of()
                            )
                    ))
                    .toList();

            ctx.json(new StdResp(true, "Available tools", Map.of("tools", toolsForResponse)));
        } catch (Exception e) {
            log.error("Error handling list tools request", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(new StdResp(false, "Internal error", null));
        }
    }

    /**
     * 处理 MCP 初始化请求
     */
    private void handleInitialize(Context ctx) {
        try {
            Map<String, Object> result = Map.of(
                    "protocolVersion", "2024-11-05",
                    "capabilities", Map.of(
                            "tools", Map.of("listChanged", false),
                            "logging", Map.of()
                    ),
                    "serverInfo", Map.of(
                            "name", "PeerBanHelper",
                            "version", "1.0.0"
                    )
            );
            ctx.json(new StdResp(true, "MCP server initialized", result));
        } catch (Exception e) {
            log.error("Error handling MCP initialize request", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(new StdResp(false, "Error initializing MCP server: " + e.getMessage(), null));
        }
    }

    /**
     * 处理工具调用请求
     */
    private void handleInvokeTool(Context ctx) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> requestBody = ctx.bodyAsClass(Map.class);
            String toolName = (String) requestBody.get("name");
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) requestBody.get("arguments");

            if (toolName == null) {
                ctx.status(HttpStatus.BAD_REQUEST);
                ctx.json(new StdResp(false, "Tool name is required", null));
                return;
            }

            final Map<String, Object> finalArguments = arguments != null ? arguments : Map.of();

            // 使用注册中心查找并执行工具
            Map<String, Object> result = toolsRegistry.getToolHandler(toolName)
                    .map(handler -> handler.apply(finalArguments))
                    .orElse(Map.of(
                            "content", List.of(Map.of(
                                    "type", "text",
                                    "text", "Unknown tool: " + toolName
                            )),
                            "isError", true
                    ));

            ctx.json(new StdResp(true, "Tool executed", result));

        } catch (Exception e) {
            log.error("Error handling tool invocation", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(new StdResp(false, "Error executing tool: " + e.getMessage(), null));
        }
    }

    // HTTP 工具调用的实现
    private Map<String, Object> handleGetStatusTool(Map<String, Object> arguments) {
        try {
            Map<String, Object> status = Map.of(
                    "running", true,
                    "timestamp", System.currentTimeMillis(),
                    "version", "8.0.8",
                    "webui_port", javalinWebContainer.javalin().port(),
                    "uptime_ms", System.currentTimeMillis() // 简化的运行时间
            );

            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "PeerBanHelper Status:\n" +
                                    "- Running: " + status.get("running") + "\n" +
                                    "- Version: " + status.get("version") + "\n" +
                                    "- WebUI Port: " + status.get("webui_port") + "\n" +
                                    "- Timestamp: " + status.get("timestamp")
                    )),
                    "isError", false
            );
        } catch (Exception e) {
            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "Error getting status: " + e.getMessage()
                    )),
                    "isError", true
            );
        }
    }

    private Map<String, Object> handleGetConfigTool(Map<String, Object> arguments) {
        try {
            Map<String, Object> config = Map.of(
                    "webui_enabled", true,
                    "port", javalinWebContainer.javalin().port(),
                    "mcp_enabled", true,
                    "token_auth", javalinWebContainer.getToken() != null && !javalinWebContainer.getToken().isBlank()
            );

            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "PeerBanHelper Configuration:\n" +
                                    "- WebUI Enabled: " + config.get("webui_enabled") + "\n" +
                                    "- Port: " + config.get("port") + "\n" +
                                    "- MCP Enabled: " + config.get("mcp_enabled") + "\n" +
                                    "- Token Auth: " + config.get("token_auth")
                    )),
                    "isError", false
            );
        } catch (Exception e) {
            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "Error getting config: " + e.getMessage()
                    )),
                    "isError", true
            );
        }
    }

    private Map<String, Object> handleGetPeerStatisticsTool(Map<String, Object> arguments) {
        try {
            // 这里应该集成实际的 PeerBanHelper 统计数据
            // 目前返回示例数据
            Map<String, Object> stats = Map.of(
                    "note", "This is mock data - integrate with actual PBH statistics",
                    "total_banned_peers", "N/A (not yet integrated)",
                    "active_sessions", "N/A (not yet integrated)",
                    "ban_rules_count", "N/A (not yet integrated)"
            );

            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "PeerBanHelper Statistics:\n" +
                                    "Note: " + stats.get("note") + "\n" +
                                    "- Total Banned Peers: " + stats.get("total_banned_peers") + "\n" +
                                    "- Active Sessions: " + stats.get("active_sessions") + "\n" +
                                    "- Ban Rules Count: " + stats.get("ban_rules_count")
                    )),
                    "isError", false
            );
        } catch (Exception e) {
            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "Error getting statistics: " + e.getMessage()
                    )),
                    "isError", true
            );
        }
    }
}
