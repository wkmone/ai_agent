package com.wk.agent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wk.agent.mcp.model.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class McpToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(McpToolRegistry.class);

    private final Map<String, McpToolCallback> tools = new ConcurrentHashMap<>();
    private final Map<Long, List<Tool>> serverTools = new ConcurrentHashMap<>();

    public void registerTool(Long serverId, Tool tool, McpClient client) {
        String toolKey = serverId + ":" + tool.getName();
        McpToolCallback callback = new McpToolCallback(serverId, tool, client);
        tools.put(toolKey, callback);
        
        serverTools.computeIfAbsent(serverId, k -> new ArrayList<>()).add(tool);
        
        logger.info("Registered MCP tool: {} from server {}", tool.getName(), serverId);
    }

    public void unregisterToolsForServer(Long serverId) {
        tools.keySet().removeIf(key -> key.startsWith(serverId + ":"));
        serverTools.remove(serverId);
        logger.info("Unregistered all MCP tools for server: {}", serverId);
    }

    public List<ToolCallback> getToolCallbacks() {
        return new ArrayList<>(tools.values());
    }

    public List<Tool> getToolsForServer(Long serverId) {
        return serverTools.getOrDefault(serverId, Collections.emptyList());
    }

    public boolean hasTools() {
        return !tools.isEmpty();
    }

    public static class McpToolCallback implements ToolCallback {

        private static final ObjectMapper objectMapper = new ObjectMapper();
        
        private final Long serverId;
        private final Tool tool;
        private final McpClient client;
        private final ToolDefinition toolDefinition;

        public McpToolCallback(Long serverId, Tool tool, McpClient client) {
            this.serverId = serverId;
            this.tool = tool;
            this.client = client;
            
            String inputSchemaJson = "{}";
            if (tool.getInputSchema() != null) {
                try {
                    inputSchemaJson = objectMapper.writeValueAsString(tool.getInputSchema());
                } catch (Exception e) {
                    LoggerFactory.getLogger(McpToolCallback.class).warn("Failed to serialize inputSchema", e);
                }
            }
            
            this.toolDefinition = ToolDefinition.builder()
                    .name(tool.getName())
                    .description(tool.getDescription())
                    .inputSchema(inputSchemaJson)
                    .build();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return toolDefinition;
        }

        public String call(String arguments) {
            try {
                logger.info("Executing MCP tool: {} with args: {}", tool.getName(), arguments);
                Map<String, Object> argMap = new HashMap<>();
                if (arguments != null && !arguments.isEmpty()) {
                    try {
                        argMap = new com.fasterxml.jackson.databind.ObjectMapper().readValue(arguments, Map.class);
                    } catch (Exception e) {
                        logger.warn("Failed to parse arguments as JSON, using empty map");
                    }
                }
                var result = client.callTool(tool.getName(), argMap);

                StringBuilder responseText = new StringBuilder();
                if (result.getContent() != null) {
                    for (var content : result.getContent()) {
                        if ("text".equals(content.getType()) && content.getText() != null) {
                            responseText.append(content.getText());
                        }
                    }
                }

                if (result.isIsError()) {
                    throw new RuntimeException("MCP tool execution failed: " + responseText);
                }

                return responseText.toString();
            } catch (Exception e) {
                logger.error("Error executing MCP tool: {}", tool.getName(), e);
                throw new RuntimeException("Failed to execute MCP tool: " + tool.getName(), e);
            }
        }
    }
}
