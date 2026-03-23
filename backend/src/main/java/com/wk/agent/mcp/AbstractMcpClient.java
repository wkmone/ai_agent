package com.wk.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wk.agent.entity.McpServer;
import com.wk.agent.mcp.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractMcpClient implements McpClient {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractMcpClient.class);

    protected final McpServer server;
    protected final ObjectMapper objectMapper;
    protected final AtomicLong requestId = new AtomicLong(1);
    protected volatile boolean connected = false;

    public AbstractMcpClient(McpServer server, ObjectMapper objectMapper) {
        this.server = server;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void close() throws Exception {
        disconnect();
    }

    protected ObjectNode createRequest(String method, Object params) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", requestId.getAndIncrement());
        request.put("method", method);
        if (params != null) {
            request.set("params", objectMapper.valueToTree(params));
        }
        return request;
    }

    protected ObjectNode createNotification(String method, Object params) {
        ObjectNode notification = objectMapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        if (params != null) {
            notification.set("params", objectMapper.valueToTree(params));
        }
        return notification;
    }

    protected Map<String, Object> parseConfig() {
        try {
            if (server.getConfig() == null || server.getConfig().trim().isEmpty()) {
                return new HashMap<>();
            }
            return objectMapper.readValue(server.getConfig(), Map.class);
        } catch (IOException e) {
            logger.error("Failed to parse server config", e);
            return new HashMap<>();
        }
    }

    protected <T> T parseResponse(JsonNode response, Class<T> valueType) throws IOException {
        if (response.has("error")) {
            JsonNode error = response.get("error");
            String message = error.has("message") ? error.get("message").asText() : "Unknown error";
            throw new IOException("MCP Error: " + message);
        }
        JsonNode result = response.get("result");
        return objectMapper.treeToValue(result, valueType);
    }

    protected Map<String, Object> createInitializeParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("protocolVersion", "2024-11-05");
        params.put("capabilities", Map.of(
            "tools", Map.of("listChanged", true),
            "resources", Map.of("listChanged", true, "subscribe", true),
            "prompts", Map.of("listChanged", true)
        ));
        params.put("clientInfo", Map.of(
            "name", "AI Agent MCP Client",
            "version", "1.0.0"
        ));
        return params;
    }
}
