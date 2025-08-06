package com.ghostchu.peerbanhelper.module.impl.ai.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * MCP工具注册中心
 * 负责管理所有MCP工具的注册、查找和元数据管理
 */
@Slf4j
@Component
public class MCPToolsRegistry {

    private final List<McpSchema.Tool> registeredTools;
    private final Map<String, Function<Map<String, Object>, Map<String, Object>>> toolHandlers;

    public MCPToolsRegistry() {
        this.registeredTools = new ArrayList<>();
        this.toolHandlers = new java.util.HashMap<>();
    }

    /**
     * 注册一个MCP工具
     * @param name 工具名称
     * @param description 工具描述
     * @param handler 工具处理器
     */
    public void registerTool(String name, String description, McpSchema.JsonSchema jsonSchema, Function<Map<String, Object>, Map<String, Object>> handler) {
        McpSchema.Tool tool = createTool(name, description, jsonSchema);
        registeredTools.add(tool);
        toolHandlers.put(name, handler);
        log.debug("Registered MCP tool: {} - {}", name, description);
    }

    /**
     * 获取所有已注册的工具
     */
    public List<McpSchema.Tool> getAllTools() {
        return List.copyOf(registeredTools);
    }

    /**
     * 根据名称查找工具
     */
    public Optional<McpSchema.Tool> findTool(String name) {
        return registeredTools.stream()
                .filter(tool -> tool.name().equals(name))
                .findFirst();
    }

    /**
     * 根据名称获取工具处理器
     */
    public Optional<Function<Map<String, Object>, Map<String, Object>>> getToolHandler(String name) {
        return Optional.ofNullable(toolHandlers.get(name));
    }

    /**
     * 检查工具是否存在
     */
    public boolean hasToolHandler(String name) {
        return toolHandlers.containsKey(name);
    }

    /**
     * 获取已注册工具的数量
     */
    public int getToolCount() {
        return registeredTools.size();
    }

    /**
     * 清空所有已注册的工具
     */
    public void clearAll() {
        registeredTools.clear();
        toolHandlers.clear();
        log.debug("Cleared all registered MCP tools");
    }

    /**
     * 创建MCP工具定义
     */
    private McpSchema.Tool createTool(String name, String description, McpSchema.JsonSchema inputSchema) {
        return new McpSchema.Tool(name, description, inputSchema);
    }
}
