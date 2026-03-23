package com.wk.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wk.agent.entity.McpServer;
import com.wk.agent.mcp.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class StreamableHttpMcpClient extends AbstractMcpClient {

    private static final Logger logger = LoggerFactory.getLogger(StreamableHttpMcpClient.class);

    private HttpClient httpClient;
    private String baseUrl;
    private String mcpSessionId;

    public StreamableHttpMcpClient(McpServer server, ObjectMapper objectMapper) {
        super(server, objectMapper);
    }

    @Override
    public void connect() throws Exception {
        logger.info("Connecting to MCP server via Streamable HTTP: {}", server.getName());
        
        Map<String, Object> config = parseConfig();
        baseUrl = (String) config.get("url");
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalArgumentException("URL is required for Streamable HTTP transport");
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        
        connected = true;
        logger.info("Successfully connected to MCP server: {}", server.getName());
        
        initialize();
    }

    @Override
    public void disconnect() throws Exception {
        connected = false;
        logger.info("Disconnected from MCP server: {}", server.getName());
    }

    @Override
    public InitializeResult initialize() throws Exception {
        logger.info("Initializing MCP session for: {}", server.getName());
        
        Map<String, Object> params = createInitializeParams();
        JsonNode response = sendRequest("initialize", params);
        InitializeResult result = parseResponse(response, InitializeResult.class);
        
        logger.info("Sending initialized notification for: {}", server.getName());
        sendNotification("notifications/initialized", null);
        
        listTools();
        
        return result;
    }

    @Override
    public List<Tool> listTools() throws Exception {
        logger.info("Listing tools for MCP server: {}", server.getName());
        
        JsonNode response = sendRequest("tools/list", null);
        
        record ToolsListResult(List<Tool> tools) {}
        ToolsListResult result = parseResponse(response, ToolsListResult.class);
        return result.tools();
    }

    @Override
    public CallToolResult callTool(String toolName, Map<String, Object> arguments) throws Exception {
        logger.info("Calling tool: {} with arguments: {}", toolName, arguments);
        
        Map<String, Object> params = Map.of(
                "name", toolName,
                "arguments", arguments
        );
        
        JsonNode response = sendRequest("tools/call", params);
        return parseResponse(response, CallToolResult.class);
    }

    @Override
    public List<Resource> listResources() throws Exception {
        logger.info("Listing resources for MCP server: {}", server.getName());
        
        JsonNode response = sendRequest("resources/list", null);
        
        record ResourcesListResult(List<Resource> resources) {}
        ResourcesListResult result = parseResponse(response, ResourcesListResult.class);
        return result.resources();
    }

    @Override
    public ReadResourceResult readResource(String uri) throws Exception {
        logger.info("Reading resource: {}", uri);
        
        Map<String, Object> params = Map.of("uri", uri);
        JsonNode response = sendRequest("resources/read", params);
        return parseResponse(response, ReadResourceResult.class);
    }

    @Override
    public List<Prompt> listPrompts() throws Exception {
        logger.info("Listing prompts for MCP server: {}", server.getName());
        
        JsonNode response = sendRequest("prompts/list", null);
        
        record PromptsListResult(List<Prompt> prompts) {}
        PromptsListResult result = parseResponse(response, PromptsListResult.class);
        return result.prompts();
    }

    @Override
    public GetPromptResult getPrompt(String name, Map<String, String> arguments) throws Exception {
        logger.info("Getting prompt: {} with arguments: {}", name, arguments);
        
        Map<String, Object> params = Map.of(
                "name", name,
                "arguments", arguments
        );
        
        JsonNode response = sendRequest("prompts/get", params);
        return parseResponse(response, GetPromptResult.class);
    }

    private void sendNotification(String method, Object params) throws Exception {
        ObjectNode notification = createNotification(method, params);
        
        String requestBody = objectMapper.writeValueAsString(notification);
        logger.info("Sending MCP notification: method={}, request={}", method, requestBody);
        
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json,text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));
        
        if (mcpSessionId != null && !mcpSessionId.isEmpty()) {
            requestBuilder.header("Mcp-Session-Id", mcpSessionId);
        }
        
        HttpRequest httpRequest = requestBuilder.build();
        
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        
        logger.info("Received notification response status: {}", response.statusCode());
        
        String sessionId = response.headers().firstValue("Mcp-Session-Id").orElse(null);
        if (sessionId != null && !sessionId.isEmpty()) {
            mcpSessionId = sessionId;
            logger.info("Received Mcp-Session-Id: {}", mcpSessionId);
        }
        
        if (response.statusCode() >= 400) {
            throw new RuntimeException("MCP notification failed: " + response.statusCode() + " - " + response.body());
        }
    }

    private JsonNode sendRequest(String method, Object params) throws Exception {
        ObjectNode request = createRequest(method, params);
        
        String requestBody = objectMapper.writeValueAsString(request);
        logger.info("Sending MCP request: method={}, request={}", method, requestBody);
        
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json,text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));
        
        if (mcpSessionId != null && !mcpSessionId.isEmpty()) {
            requestBuilder.header("Mcp-Session-Id", mcpSessionId);
        }
        
        HttpRequest httpRequest = requestBuilder.build();
        
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        
        logger.info("Received response status: {}", response.statusCode());
        
        String sessionId = response.headers().firstValue("Mcp-Session-Id").orElse(null);
        if (sessionId != null && !sessionId.isEmpty()) {
            mcpSessionId = sessionId;
            logger.info("Received Mcp-Session-Id: {}", mcpSessionId);
        }
        
        String responseBody = response.body();
        logger.info("Received MCP response: {}", responseBody);
        
        if (response.statusCode() >= 400) {
            throw new RuntimeException("MCP request failed: " + response.statusCode() + " - " + responseBody);
        }
        
        return objectMapper.readTree(responseBody);
    }
}
