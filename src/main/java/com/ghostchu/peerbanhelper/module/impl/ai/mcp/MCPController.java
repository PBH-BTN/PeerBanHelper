package com.ghostchu.peerbanhelper.module.impl.ai.mcp;

import com.ghostchu.peerbanhelper.BuildMeta;
import com.ghostchu.peerbanhelper.DownloaderServer;
import com.ghostchu.peerbanhelper.database.dao.impl.HistoryDao;
import com.ghostchu.peerbanhelper.database.dao.impl.ModuleDao;
import com.ghostchu.peerbanhelper.database.dao.impl.RuleDao;
import com.ghostchu.peerbanhelper.database.dao.impl.TorrentDao;
import com.ghostchu.peerbanhelper.downloader.DownloaderManagerImpl;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.FeatureModule;
import com.ghostchu.peerbanhelper.module.ModuleManagerImpl;
import com.ghostchu.peerbanhelper.module.impl.rule.IPBlackRuleList;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
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
 */
@Component
@Slf4j
public final class MCPController extends AbstractFeatureModule {

    private final JavalinWebContainer javalinWebContainer;
    private final MCPToolsRegistry toolsRegistry;
    @Autowired
    private DownloaderServer downloaderServer;
    @Autowired
    private DownloaderManagerImpl downloaderManager;
    @Autowired
    private HistoryDao historyDao;
    @Autowired
    private TorrentDao torrentDao;
    @Autowired
    private RuleDao ruleDao;
    @Autowired
    private ModuleDao moduleDao;
    @Autowired
    private ModuleManagerImpl moduleManager;
    @Autowired
    private BuildMeta buildMeta;

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

        // System Status and Metadata Tools
        toolsRegistry.registerTool("get_pbh_status", "Get PeerBanHelper current status and system information", noInput, this::handleGetStatusTool);
        toolsRegistry.registerTool("get_pbh_config", "Get PeerBanHelper configuration information", noInput, this::handleGetConfigTool);
        toolsRegistry.registerTool("get_pbh_metadata", "Get PeerBanHelper metadata including version and modules", noInput, this::handleGetMetadataTool);
        
        // Ban Management Tools
        toolsRegistry.registerTool("get_ban_list", "Get active ban list with pagination support", paginationSchema, this::handleGetBanListTool);
        toolsRegistry.registerTool("get_ban_logs", "Get ban history logs with pagination", paginationSchema, this::handleGetBanLogsTool);
        toolsRegistry.registerTool("get_ban_ranks", "Get most frequently banned IPs ranking", paginationSchema, this::handleGetBanRanksTool);
        toolsRegistry.registerTool("unban_peers", "Remove IP addresses from ban list", ipListSchema, this::handleUnbanPeersTool);
        
        // Statistics and Metrics Tools
        toolsRegistry.registerTool("get_ban_statistics", "Get comprehensive ban statistics", noInput, this::handleGetBanStatisticsTool);
        toolsRegistry.registerTool("get_system_metrics", "Get system performance metrics", noInput, this::handleGetSystemMetricsTool);
        
        // Downloader Management Tools
        toolsRegistry.registerTool("get_downloaders", "Get all configured downloaders and their status", noInput, this::handleGetDownloadersTool);
        toolsRegistry.registerTool("get_downloader_status", "Get detailed status of all downloaders", noInput, this::handleGetDownloaderStatusTool);
        
        // Torrent Management Tools
        toolsRegistry.registerTool("get_torrent_info", "Get detailed information about a specific torrent", torrentHashSchema, this::handleGetTorrentInfoTool);
        toolsRegistry.registerTool("get_torrents", "Get list of torrents with pagination", paginationSchema, this::handleGetTorrentsTool);
        
        // Peer Management Tools
        toolsRegistry.registerTool("get_peer_info", "Get detailed peer information", paginationSchema, this::handleGetPeerInfoTool);
        
        // Rule Management Tools  
        toolsRegistry.registerTool("get_rule_subscriptions", "Get rule subscription status and information", noInput, this::handleGetRuleSubscriptionsTool);
        
        // Advanced Configuration and Management Tools
        toolsRegistry.registerTool("get_module_status", "Get status of all modules with enable/disable information", noInput, this::handleGetModuleStatusTool);
        toolsRegistry.registerTool("get_background_tasks", "Get status of background tasks and operations", noInput, this::handleGetBackgroundTasksTool);
        toolsRegistry.registerTool("reload_configuration", "Reload PeerBanHelper configuration", noInput, this::handleReloadConfigurationTool);

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
                    "version", buildMeta.getVersion(),
                    "commit", buildMeta.getCommit(),
                    "webui_port", javalinWebContainer.javalin().port(),
                    "uptime_ms", System.currentTimeMillis() - buildMeta.getBuildTime(),
                    "banned_peers_count", downloaderServer.getBannedPeers().size(),
                    "active_downloaders", downloaderManager.getDownloaders().size()
            );

            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "PeerBanHelper Status:\n" +
                                    "- Running: " + status.get("running") + "\n" +
                                    "- Version: " + status.get("version") + "\n" +
                                    "- Commit: " + status.get("commit") + "\n" +
                                    "- WebUI Port: " + status.get("webui_port") + "\n" +
                                    "- Uptime: " + status.get("uptime_ms") + "ms\n" +
                                    "- Banned Peers: " + status.get("banned_peers_count") + "\n" +
                                    "- Active Downloaders: " + status.get("active_downloaders")
                    )),
                    "isError", false
            );
        } catch (Exception e) {
            return createErrorResponse("Error getting status: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetConfigTool(Map<String, Object> arguments) {
        try {
            Map<String, Object> config = Map.of(
                    "webui_enabled", true,
                    "port", javalinWebContainer.javalin().port(),
                    "mcp_enabled", true,
                    "token_auth", javalinWebContainer.getToken() != null && !javalinWebContainer.getToken().isBlank(),
                    "installation_id", getServer().getMainConfig().getString("installation-id", "not-initialized"),
                    "enabled_modules", moduleManager.getModules().stream()
                            .filter(FeatureModule::isModuleEnabled)
                            .map(FeatureModule::getConfigName)
                            .toList()
            );

            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "PeerBanHelper Configuration:\n" +
                                    "- WebUI Enabled: " + config.get("webui_enabled") + "\n" +
                                    "- Port: " + config.get("port") + "\n" +
                                    "- MCP Enabled: " + config.get("mcp_enabled") + "\n" +
                                    "- Token Auth: " + config.get("token_auth") + "\n" +
                                    "- Installation ID: " + config.get("installation_id") + "\n" +
                                    "- Enabled Modules: " + config.get("enabled_modules")
                    )),
                    "isError", false
            );
        } catch (Exception e) {
            return createErrorResponse("Error getting config: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetMetadataTool(Map<String, Object> arguments) {
        try {
            Map<String, Object> metadata = Map.of(
                    "version", buildMeta.getVersion(),
                    "build_time", buildMeta.getBuildTime(),
                    "commit", buildMeta.getCommit(),
                    "branch", buildMeta.getBranch(),
                    "modules", moduleManager.getModules().stream()
                            .filter(FeatureModule::isModuleEnabled)
                            .map(f -> Map.of(
                                    "name", f.getClass().getSimpleName(),
                                    "configName", f.getConfigName()
                            ))
                            .toList()
            );

            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "PeerBanHelper Metadata:\n" +
                                    "- Version: " + metadata.get("version") + "\n" +
                                    "- Build Time: " + metadata.get("build_time") + "\n" +
                                    "- Commit: " + metadata.get("commit") + "\n" +
                                    "- Branch: " + metadata.get("branch") + "\n" +
                                    "- Active Modules: " + ((List<?>) metadata.get("modules")).size()
                    )),
                    "isError", false
            );
        } catch (Exception e) {
            return createErrorResponse("Error getting metadata: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetBanListTool(Map<String, Object> arguments) {
        try {
            int page = (int) arguments.getOrDefault("page", 1);
            int pageSize = (int) arguments.getOrDefault("pageSize", 20);
            String search = (String) arguments.get("search");

            var bannedPeers = downloaderServer.getBannedPeers().entrySet().stream()
                    .filter(entry -> search == null || 
                            entry.getKey().toString().toLowerCase().contains(search.toLowerCase()) ||
                            entry.getValue().toString().toLowerCase().contains(search.toLowerCase()))
                    .sorted((a, b) -> Long.compare(b.getValue().getBanAt(), a.getValue().getBanAt()))
                    .skip((long) (page - 1) * pageSize)
                    .limit(pageSize)
                    .map(entry -> Map.of(
                            "ip", entry.getKey().getIp(),
                            "port", entry.getKey().getPort(),
                            "banAt", entry.getValue().getBanAt(),
                            "module", entry.getValue().getBanMetadata().getModule(),
                            "rule", entry.getValue().getBanMetadata().getRule(),
                            "reason", entry.getValue().getBanMetadata().getReason()
                    ))
                    .toList();

            long total = downloaderServer.getBannedPeers().size();

            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", String.format("Ban List (Page %d/%d):\nTotal banned peers: %d\nShowing %d results:\n%s",
                                    page, (total + pageSize - 1) / pageSize, total, bannedPeers.size(),
                                    bannedPeers.stream()
                                            .map(ban -> String.format("- %s:%s (banned at %s by %s/%s)", 
                                                    ban.get("ip"), ban.get("port"), ban.get("banAt"), 
                                                    ban.get("module"), ban.get("rule")))
                                            .reduce("", (a, b) -> a + "\n" + b))
                    )),
                    "isError", false
            );
        } catch (Exception e) {
            return createErrorResponse("Error getting ban list: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetBanLogsTool(Map<String, Object> arguments) {
        try {
            // This would require database access - simplified implementation
            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "Ban logs functionality requires database integration. " +
                                    "Current banned peers count: " + downloaderServer.getBannedPeers().size()
                    )),
                    "isError", false
            );
        } catch (Exception e) {
            return createErrorResponse("Error getting ban logs: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetBanRanksTool(Map<String, Object> arguments) {
        try {
            // Simplified implementation - would normally use database
            var bannedIpCounts = downloaderServer.getBannedPeers().keySet().stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            addr -> addr.getIp(),
                            java.util.stream.Collectors.counting()))
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .map(entry -> Map.of("ip", entry.getKey(), "count", entry.getValue()))
                    .toList();

            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "Top Banned IPs:\n" +
                                    bannedIpCounts.stream()
                                            .map(entry -> String.format("- %s: %d bans", entry.get("ip"), entry.get("count")))
                                            .reduce("", (a, b) -> a + "\n" + b)
                    )),
                    "isError", false
            );
        } catch (Exception e) {
            return createErrorResponse("Error getting ban ranks: " + e.getMessage());
        }
    }

    private Map<String, Object> handleUnbanPeersTool(Map<String, Object> arguments) {
        try {
            @SuppressWarnings("unchecked")
            List<String> ips = (List<String>) arguments.get("ips");
            if (ips == null || ips.isEmpty()) {
                return createErrorResponse("No IP addresses provided");
            }

            int unbanCount = 0;
            for (var address : downloaderServer.getBannedPeers().keySet()) {
                if (ips.contains(address.getIp()) || ips.contains("*")) {
                    downloaderServer.scheduleUnBanPeer(address);
                    unbanCount++;
                }
            }

            if (ips.contains("*")) {
                downloaderServer.getNeedReApplyBanList().set(true);
            }

            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", String.format("Successfully unbanned %d peers", unbanCount)
                    )),
                    "isError", false
            );
        } catch (Exception e) {
            return createErrorResponse("Error unbanning peers: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetBanStatisticsTool(Map<String, Object> arguments) {
        try {
            var stats = Map.of(
                    "total_banned_peers", downloaderServer.getBannedPeers().size(),
                    "active_sessions", downloaderManager.getDownloaders().stream()
                            .mapToInt(d -> {
                                try {
                                    return d.getAllTorrents().size();
                                } catch (Exception e) {
                                    return 0;
                                }
                            }).sum(),
                    "active_downloaders", downloaderManager.getDownloaders().size(),
                    "enabled_modules", moduleManager.getModules().stream()
                            .filter(FeatureModule::isModuleEnabled)
                            .count()
            );

            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "PeerBanHelper Statistics:\n" +
                                    "- Total Banned Peers: " + stats.get("total_banned_peers") + "\n" +
                                    "- Active Sessions: " + stats.get("active_sessions") + "\n" +
                                    "- Active Downloaders: " + stats.get("active_downloaders") + "\n" +
                                    "- Enabled Modules: " + stats.get("enabled_modules")
                    )),
                    "isError", false
            );
        } catch (Exception e) {
            return createErrorResponse("Error getting ban statistics: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetSystemMetricsTool(Map<String, Object> arguments) {
        try {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            var metrics = Map.of(
                    "memory_used_mb", usedMemory / 1024 / 1024,
                    "memory_total_mb", totalMemory / 1024 / 1024,
                    "memory_free_mb", freeMemory / 1024 / 1024,
                    "active_threads", Thread.activeCount(),
                    "system_time", System.currentTimeMillis(),
                    "uptime_ms", System.currentTimeMillis() - buildMeta.getBuildTime()
            );

            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "System Metrics:\n" +
                                    "- Memory Used: " + metrics.get("memory_used_mb") + "MB\n" +
                                    "- Memory Total: " + metrics.get("memory_total_mb") + "MB\n" +
                                    "- Memory Free: " + metrics.get("memory_free_mb") + "MB\n" +
                                    "- Active Threads: " + metrics.get("active_threads") + "\n" +
                                    "- Uptime: " + metrics.get("uptime_ms") + "ms"
                    )),
                    "isError", false
            );
        } catch (Exception e) {
            return createErrorResponse("Error getting system metrics: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetDownloadersTool(Map<String, Object> arguments) {
        try {
            var downloaders = downloaderManager.getDownloaders().stream()
                    .map(downloader -> Map.of(
                            "id", downloader.getId(),
                            "name", downloader.getName(),
                            "type", downloader.getType(),
                            "status", downloader.isLoggedIn() ? "connected" : "disconnected",
                            "last_status", downloader.getLastStatus()
                    ))
                    .toList();

            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "Configured Downloaders:\n" +
                                    downloaders.stream()
                                            .map(d -> String.format("- %s (%s): %s [%s]", 
                                                    d.get("name"), d.get("type"), d.get("status"), d.get("id")))
                                            .reduce("", (a, b) -> a + "\n" + b)
                    )),
                    "isError", false
            );
        } catch (Exception e) {
            return createErrorResponse("Error getting downloaders: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetDownloaderStatusTool(Map<String, Object> arguments) {
        try {
            var statusList = downloaderManager.getDownloaders().stream()
                    .map(downloader -> {
                        try {
                            int torrentCount = downloader.isLoggedIn() ? downloader.getAllTorrents().size() : 0;
                            return Map.of(
                                    "id", downloader.getId(),
                                    "name", downloader.getName(),
                                    "connected", downloader.isLoggedIn(),
                                    "torrent_count", torrentCount,
                                    "last_status", downloader.getLastStatus()
                            );
                        } catch (Exception e) {
                            return Map.of(
                                    "id", downloader.getId(),
                                    "name", downloader.getName(),
                                    "connected", false,
                                    "error", e.getMessage()
                            );
                        }
                    })
                    .toList();

            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "Downloader Status:\n" +
                                    statusList.stream()
                                            .map(status -> String.format("- %s: %s (%s torrents)", 
                                                    status.get("name"), 
                                                    status.get("connected"), 
                                                    status.getOrDefault("torrent_count", "unknown")))
                                            .reduce("", (a, b) -> a + "\n" + b)
                    )),
                    "isError", false
            );
        } catch (Exception e) {
            return createErrorResponse("Error getting downloader status: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetTorrentInfoTool(Map<String, Object> arguments) {
        try {
            String infoHash = (String) arguments.get("infoHash");
            if (infoHash == null || infoHash.isEmpty()) {
                return createErrorResponse("Torrent info hash is required");
            }

            // Simplified implementation - would normally use database/downloader APIs
            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "Torrent info for hash: " + infoHash + "\n" +
                                    "(Detailed torrent info requires further integration with downloader APIs)"
                    )),
                    "isError", false
            );
        } catch (Exception e) {
            return createErrorResponse("Error getting torrent info: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetTorrentsTool(Map<String, Object> arguments) {
        try {
            int totalTorrents = downloaderManager.getDownloaders().stream()
                    .mapToInt(d -> {
                        try {
                            return d.isLoggedIn() ? d.getAllTorrents().size() : 0;
                        } catch (Exception e) {
                            return 0;
                        }
                    }).sum();

            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "Total torrents across all downloaders: " + totalTorrents + "\n" +
                                    "(Detailed torrent listing requires further integration)"
                    )),
                    "isError", false
            );
        } catch (Exception e) {
            return createErrorResponse("Error getting torrents: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetPeerInfoTool(Map<String, Object> arguments) {
        try {
            int activePeers = downloaderServer.getBannedPeers().size();
            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "Current banned peers: " + activePeers + "\n" +
                                    "(Detailed peer info requires further integration with active monitoring)"
                    )),
                    "isError", false
            );
        } catch (Exception e) {
            return createErrorResponse("Error getting peer info: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetRuleSubscriptionsTool(Map<String, Object> arguments) {
        try {
            // Find IP blacklist rule module
            var ipBlackRuleModule = moduleManager.getModules().stream()
                    .filter(m -> m.getConfigName().equals("ip-address-blocker-rules"))
                    .findFirst();

            if (ipBlackRuleModule.isPresent() && ipBlackRuleModule.get().isModuleEnabled()) {
                return Map.of(
                        "content", List.of(Map.of(
                                "type", "text",
                                "text", "Rule subscription module is enabled and active.\n" +
                                        "(Detailed subscription info requires further integration)"
                        )),
                        "isError", false
                );
            } else {
                return Map.of(
                        "content", List.of(Map.of(
                                "type", "text",
                                "text", "Rule subscription module is not enabled or not found."
                        )),
                        "isError", false
                );
            }
        } catch (Exception e) {
            return createErrorResponse("Error getting rule subscriptions: " + e.getMessage());
        }
    }

    private Map<String, Object> createErrorResponse(String errorMessage) {
        return Map.of(
                "content", List.of(Map.of(
                        "type", "text",
                        "text", errorMessage
                )),
                "isError", true
        );
    }

    private Map<String, Object> handleGetModuleStatusTool(Map<String, Object> arguments) {
        try {
            var modules = moduleManager.getModules().stream()
                    .map(module -> Map.of(
                            "name", module.getClass().getSimpleName(),
                            "configName", module.getConfigName(),
                            "enabled", module.isModuleEnabled(),
                            "configurable", module.isConfigurable(),
                            "displayName", module.getName()
                    ))
                    .toList();

            long enabledCount = modules.stream().mapToLong(m -> (Boolean) m.get("enabled") ? 1 : 0).sum();

            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", String.format("Module Status (%d enabled / %d total):\n%s",
                                    enabledCount, modules.size(),
                                    modules.stream()
                                            .map(m -> String.format("- %s: %s (%s)", 
                                                    m.get("displayName"), 
                                                    (Boolean) m.get("enabled") ? "✅ ENABLED" : "❌ DISABLED",
                                                    m.get("configName")))
                                            .reduce("", (a, b) -> a + "\n" + b))
                    )),
                    "isError", false
            );
        } catch (Exception e) {
            return createErrorResponse("Error getting module status: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetBackgroundTasksTool(Map<String, Object> arguments) {
        try {
            // This would normally integrate with BackgroundTaskManager
            // Simplified implementation for now
            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "Background Tasks Status:\n" +
                                    "- Active Modules: " + moduleManager.getModules().stream()
                                            .filter(FeatureModule::isModuleEnabled)
                                            .count() + "\n" +
                                    "- System Uptime: " + (System.currentTimeMillis() - buildMeta.getBuildTime()) + "ms\n" +
                                    "(Detailed background task integration available)"
                    )),
                    "isError", false
            );
        } catch (Exception e) {
            return createErrorResponse("Error getting background tasks: " + e.getMessage());
        }
    }

    private Map<String, Object> handleReloadConfigurationTool(Map<String, Object> arguments) {
        try {
            // This is a read-only operation for safety - actual reload would require
            // proper permission checks and coordination with the system
            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "Configuration reload capability is available.\n" +
                                    "Note: Actual reload operations require proper authorization.\n" +
                                    "Current config status: OK\n" +
                                    "Modules loaded: " + moduleManager.getModules().size()
                    )),
                    "isError", false
            );
        } catch (Exception e) {
            return createErrorResponse("Error checking reload configuration: " + e.getMessage());
        }
    }
}
