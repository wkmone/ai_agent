package com.wk.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wk.agent.entity.McpServer;
import com.wk.agent.mcp.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class StdioMcpClient extends AbstractMcpClient {

    private static final Logger logger = LoggerFactory.getLogger(StdioMcpClient.class);

    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;
    private final Map<Long, JsonNode> pendingResponses = new ConcurrentHashMap<>();
    private final Map<Long, CountDownLatch> pendingLatches = new ConcurrentHashMap<>();
    private Thread readThread;
    private volatile boolean running = false;
    private String command;
    private List<String> args;

    public StdioMcpClient(McpServer server, ObjectMapper objectMapper) {
        super(server, objectMapper);
    }

    @Override
    public void connect() throws Exception {
        logger.info("Connecting to MCP server via STDIO: {}", server.getName());
        
        Map<String, Object> config = parseConfig();
        
        Map<String, Object> mcpServers = (Map<String, Object>) config.get("mcpServers");
        if (mcpServers == null || mcpServers.isEmpty()) {
            throw new IllegalArgumentException("mcpServers configuration is required for STDIO transport");
        }
        
        Map.Entry<String, Object> firstServer = mcpServers.entrySet().iterator().next();
        Map<String, Object> serverConfig = (Map<String, Object>) firstServer.getValue();
        
        command = (String) serverConfig.get("command");
        args = (List<String>) serverConfig.get("args");
        
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command is required for STDIO transport");
        }
        
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(command);
        if (args != null) {
            processBuilder.command().addAll(args);
        }
        
        this.process = processBuilder.start();
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        
        this.running = true;
        this.readThread = new Thread(this::readResponses, "MCP-Stdio-ReadThread");
        this.readThread.start();
        
        connected = true;
        logger.info("Successfully connected to MCP server: {}", server.getName());
        
        initialize();
    }

    private void readResponses() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                
                try {
                    JsonNode response = objectMapper.readTree(line);
                    if (response.has("id")) {
                        long id = response.get("id").asLong();
                        pendingResponses.put(id, response);
                        CountDownLatch latch = pendingLatches.remove(id);
                        if (latch != null) {
                            latch.countDown();
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to parse MCP response: {}", line, e);
                }
            }
        } catch (Exception e) {
            if (running) {
                logger.error("Error reading from MCP server", e);
            }
        }
    }

    @Override
    public void disconnect() throws Exception {
        connected = false;
        running = false;
        
        if (readThread != null) {
            readThread.interrupt();
        }
        
        if (writer != null) {
            try {
                writer.close();
            } catch (Exception e) {
                logger.warn("Error closing writer", e);
            }
        }
        
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception e) {
                logger.warn("Error closing reader", e);
            }
        }
        
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        
        pendingResponses.clear();
        pendingLatches.clear();
        logger.info("Disconnected from MCP server: {}", server.getName());
    }

    @Override
    public InitializeResult initialize() throws Exception {
        logger.info("Initializing MCP session for: {}", server.getName());
        
        Map<String, Object> params = createInitializeParams();
        JsonNode response = sendRequest("initialize", params);
        InitializeResult result = parseResponse(response, InitializeResult.class);
        
        listTools();
        
        return result;
    }

    @Override
    public List<Tool> listTools() throws Exception {
        logger.info("Listing tools for MCP server: {}", server.getName());
        
        JsonNode response = sendRequest("tools/list", Map.of());
        
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
        
        JsonNode response = sendRequest("resources/list", Map.of());
        
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
        
        JsonNode response = sendRequest("prompts/list", Map.of());
        
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

    private JsonNode sendRequest(String method, Object params) throws Exception {
        long requestId = this.requestId.getAndIncrement();
        ObjectNode request = createRequest(method, params);
        
        logger.debug("Sending MCP request: method={}, id={}", method, requestId);
        
        CountDownLatch latch = new CountDownLatch(1);
        pendingLatches.put(requestId, latch);
        
        String requestJson = objectMapper.writeValueAsString(request);
        synchronized (writer) {
            writer.write(requestJson);
            writer.newLine();
            writer.flush();
        }
        
        boolean completed = latch.await(120, TimeUnit.SECONDS);
        if (!completed) {
            pendingLatches.remove(requestId);
            pendingResponses.remove(requestId);
            throw new RuntimeException("MCP request timeout");
        }
        
        JsonNode response = pendingResponses.remove(requestId);
        
        if (response == null) {
            throw new RuntimeException("No response received for MCP request");
        }
        
        return response;
    }
}
