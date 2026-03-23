package com.wk.agent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wk.agent.entity.McpServer;
import com.wk.agent.mcp.model.Tool;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class McpClientManager {

    private static final Logger logger = LoggerFactory.getLogger(McpClientManager.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private McpToolRegistry mcpToolRegistry;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private com.wk.agent.service.McpServerService mcpServerService;

    private final Map<Long, McpClient> clients = new ConcurrentHashMap<>();

    @PreDestroy
    public void destroy() {
        logger.info("Shutting down MCP Client Manager...");
        clients.forEach((id, client) -> {
            try {
                mcpToolRegistry.unregisterToolsForServer(id);
                client.close();
            } catch (Exception e) {
                logger.error("Error closing MCP client for server {}", id, e);
            }
        });
        clients.clear();
    }

    public void initializeClient(McpServer server) {
        if (clients.containsKey(server.getId())) {
            logger.warn("MCP client already exists for server: {}", server.getName());
            return;
        }

        try {
            McpClient client = createClient(server);
            client.connect();
            clients.put(server.getId(), client);
            registerTools(server.getId(), client);
            logger.info("Successfully initialized MCP client for server: {}", server.getName());
        } catch (Exception e) {
            logger.error("Failed to initialize MCP client for server: {}", server.getName(), e);
        }
    }

    private void registerTools(Long serverId, McpClient client) {
        try {
            List<Tool> tools = client.listTools();
            for (Tool tool : tools) {
                mcpToolRegistry.registerTool(serverId, tool, client);
            }
            logger.info("Registered {} tools for server {}", tools.size(), serverId);
        } catch (Exception e) {
            logger.error("Failed to register tools for server {}", serverId, e);
        }
    }

    private McpClient createClient(McpServer server) {
        String serverType = server.getServerType();
        return switch (serverType != null ? serverType.toLowerCase() : "") {
            case "stdio" -> new StdioMcpClient(server, objectMapper);
            case "streamable" -> new StreamableHttpMcpClient(server, objectMapper);
            default -> throw new IllegalArgumentException("Unsupported server type: " + serverType);
        };
    }

    public void destroyClient(Long serverId) {
        McpClient client = clients.remove(serverId);
        if (client != null) {
            try {
                mcpToolRegistry.unregisterToolsForServer(serverId);
                client.close();
                logger.info("Successfully destroyed MCP client for server id: {}", serverId);
            } catch (Exception e) {
                logger.error("Error destroying MCP client for server id: {}", serverId, e);
            }
        }
    }

    public McpClient getClient(Long serverId) {
        return clients.get(serverId);
    }

    public Map<Long, McpClient> getAllClients() {
        return new ConcurrentHashMap<>(clients);
    }

    public McpClient getOrInitializeClient(McpServer server) {
        Long serverId = server.getId();
        if (!clients.containsKey(serverId)) {
            logger.info("Lazy initializing MCP client for server: {}", server.getName());
            initializeClient(server);
        }
        return clients.get(serverId);
    }

    public void reconnectClient(Long serverId) {
        logger.info("Reconnecting MCP client for server id: {}", serverId);
        destroyClient(serverId);
        
        McpServer server = mcpServerService.getServer(serverId);
        if (server != null && server.getEnabled()) {
            try {
                logger.info("Starting re-initialization for server: {}", server.getName());
                McpClient client = createClient(server);
                client.connect();
                clients.put(serverId, client);
                logger.info("MCP client connected for server id: {}, now registering tools...", serverId);
                registerTools(serverId, client);
                int toolCount = mcpToolRegistry.getToolsForServer(serverId).size();
                logger.info("MCP client reconnection completed for server id: {}, registered {} tools", serverId, toolCount);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Reconnection interrupted for server id: {}", serverId, e);
            } catch (Exception e) {
                logger.error("Failed to reconnect MCP client for server id: {}", serverId, e);
            }
        } else {
            logger.warn("Cannot reconnect MCP client for server id: {} - server not found or disabled", serverId);
        }
    }
}
