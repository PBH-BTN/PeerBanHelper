package com.ghostchu.peerbanhelper.temp;

// 探索MCP SDK API
import io.modelcontextprotocol.spec.McpSchema;

public class MCPExplorer {
    public static void main(String[] args) {
        // 探索可用的类和方法
        System.out.println("MCP SDK Classes:");
        
        // McpSchema 相关
        McpSchema.Tool tool;
        McpSchema.ServerCapabilities capabilities;
        McpSchema.InitializeRequest initRequest;
        McpSchema.CallToolRequest callToolRequest;
        McpSchema.TextContent textContent;
        McpSchema.Content content;
        
        System.out.println("All classes explored successfully");
    }
}
