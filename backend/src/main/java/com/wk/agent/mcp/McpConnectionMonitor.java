package com.wk.agent.mcp;

import com.wk.agent.entity.McpServer;
import com.wk.agent.service.McpServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class McpConnectionMonitor {

    private static final Logger logger = LoggerFactory.getLogger(McpConnectionMonitor.class);

    @Autowired
    private McpServerService mcpServerService;

    @Autowired
    private McpClientManager mcpClientManager;

    @Scheduled(fixedDelay = 600000)
    public void monitorConnections() {
        logger.debug("Checking MCP server connections...");
        
        List<McpServer> enabledServers = mcpServerService.listEnabledServers();
        int reconnectCount = 0;
        
        for (McpServer server : enabledServers) {
            try {
                McpClient client = mcpClientManager.getClient(server.getId());
                
                if (client == null || !client.isConnected()) {
                    logger.info("Reconnecting to MCP server: {}", server.getName());
                    mcpClientManager.destroyClient(server.getId());
                    mcpClientManager.initializeClient(server);
                    reconnectCount++;
                }
            } catch (Exception e) {
                logger.error("Error checking/reconnecting MCP server: {}", server.getName(), e);
            }
        }
        
        if (reconnectCount > 0) {
            logger.info("Reconnected {} MCP servers", reconnectCount);
        }
    }
}
