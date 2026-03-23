package com.wk.agent.mcp;

import com.wk.agent.mcp.model.*;

import java.util.List;
import java.util.Map;

public interface McpClient extends AutoCloseable {

    void connect() throws Exception;

    void disconnect() throws Exception;

    InitializeResult initialize() throws Exception;

    List<Tool> listTools() throws Exception;

    CallToolResult callTool(String toolName, Map<String, Object> arguments) throws Exception;

    List<Resource> listResources() throws Exception;

    ReadResourceResult readResource(String uri) throws Exception;

    List<Prompt> listPrompts() throws Exception;

    GetPromptResult getPrompt(String name, Map<String, String> arguments) throws Exception;

    boolean isConnected();
}
