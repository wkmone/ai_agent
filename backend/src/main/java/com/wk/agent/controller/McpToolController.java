package com.wk.agent.controller;

import com.wk.agent.mcp.McpToolRegistry;
import com.wk.agent.mcp.model.Tool;
import com.wk.agent.service.McpServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/mcp")
@Tag(name = "MCP Tools", description = "MCP 工具管理")
@CrossOrigin(origins = "*")
public class McpToolController {

    @Autowired
    private McpServerService mcpServerService;

    @Autowired
    private McpToolRegistry mcpToolRegistry;

    @GetMapping("/tools/all")
    @Operation(summary = "获取所有已启用 MCP 服务器的工具列表")
    public ResponseEntity<List<Map<String, Object>>> getAllTools() {
        var enabledServers = mcpServerService.listEnabledServers();
        List<Map<String, Object>> result = new ArrayList<>();

        for (var server : enabledServers) {
            List<Tool> tools = mcpToolRegistry.getToolsForServer(server.getId());
            for (Tool tool : tools) {
                Map<String, Object> toolMap = new HashMap<>();
                toolMap.put("name", tool.getName());
                toolMap.put("description", tool.getDescription());
                toolMap.put("serverId", server.getId());
                toolMap.put("serverName", server.getName());
                result.add(toolMap);
            }
        }

        return ResponseEntity.ok(result);
    }
}
