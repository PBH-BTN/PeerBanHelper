package com.ghostchu.peerbanhelper.module.impl.ai.mcp;

import java.util.Map;

/**
 * Base interface for MCP tool providers
 * Each provider is responsible for registering and handling tools for a specific domain
 */
public interface MCPToolProvider {
    
    /**
     * Register all tools provided by this provider
     * @param registry The MCP tools registry to register tools with
     */
    void registerTools(MCPToolsRegistry registry);
    
    /**
     * Get the provider name for logging and identification
     * @return Provider name
     */
    String getProviderName();
    
    /**
     * Create a standardized error response
     * @param message Error message
     * @return Error response map
     */
    default Map<String, Object> createErrorResponse(String message) {
        return Map.of(
                "content", Map.of(
                        "type", "text",
                        "text", "Error: " + message
                ),
                "isError", true
        );
    }
    
    /**
     * Create a standardized success response
     * @param content Response content
     * @return Success response map
     */
    default Map<String, Object> createSuccessResponse(Object content) {
        return Map.of(
                "content", content,
                "isError", false
        );
    }
}