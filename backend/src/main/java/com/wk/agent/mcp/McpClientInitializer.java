package com.wk.agent.mcp;

import com.wk.agent.entity.McpServer;
import com.wk.agent.service.McpServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class McpClientInitializer {

    private static final Logger logger = LoggerFactory.getLogger(McpClientInitializer.class);

    @Autowired
    private McpServerService mcpServerService;

    @Autowired
    private McpClientManager mcpClientManager;

    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Starting MCP client initialization in background...");
        executorService.submit(this::initializeAllServers);
        logger.info("MCP client initialization started in background");
    }

    @Async
    private void initializeAllServers() {
        logger.info("Initializing MCP clients for enabled servers...");
        try {
            List<McpServer> servers;
            try {
                servers = mcpServerService.listEnabledServers();
            } catch (Exception e) {
                logger.warn("Failed to load MCP servers from database (table may not exist yet), skipping initialization: {}", e.getMessage());
                servers = Collections.emptyList();
            }

            for (McpServer server : servers) {
                try {
                    logger.info("Initializing MCP client for server: {}", server.getName());
                    mcpClientManager.initializeClient(server);
                } catch (Exception e) {
                    logger.error("Failed to initialize MCP client for server: {}", server.getName(), e);
                }
            }
            logger.info("MCP client initialization completed");
        } catch (Exception e) {
            logger.error("Error during MCP client initialization", e);
        }
    }
}
