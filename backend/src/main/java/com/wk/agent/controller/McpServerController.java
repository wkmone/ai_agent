package com.wk.agent.controller;

import com.wk.agent.entity.McpServer;
import com.wk.agent.mcp.McpClientManager;
import com.wk.agent.mcp.McpToolRegistry;
import com.wk.agent.mcp.model.Tool;
import com.wk.agent.service.McpServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/mcp/servers")
@Tag(name = "MCP Server Management", description = "MCP 服务器管理")
@CrossOrigin(origins = "*")
public class McpServerController {

    @Autowired
    private McpServerService mcpServerService;

    @Autowired
    private McpClientManager mcpClientManager;

    @Autowired
    private McpToolRegistry mcpToolRegistry;

    @GetMapping
    @Operation(summary = "列出所有 MCP 服务器")
    public ResponseEntity<List<Map<String, Object>>> listServers() {
        List<McpServer> servers = mcpServerService.listAllServers();
        List<Map<String, Object>> result = servers.stream().map(server -> {
            Map<String, Object> serverMap = new HashMap<>();
            serverMap.put("id", server.getId());
            serverMap.put("name", server.getName());
            serverMap.put("description", server.getDescription());
            serverMap.put("serverType", server.getServerType());
            serverMap.put("config", server.getConfig());
            serverMap.put("enabled", server.getEnabled());
            serverMap.put("createdAt", server.getCreatedAt());
            serverMap.put("updatedAt", server.getUpdatedAt());
            serverMap.put("connected", mcpClientManager.getClient(server.getId()) != null && 
                    mcpClientManager.getClient(server.getId()).isConnected());
            return serverMap;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/enabled")
    @Operation(summary = "列出已启用的 MCP 服务器")
    public ResponseEntity<List<McpServer>> listEnabledServers() {
        return ResponseEntity.ok(mcpServerService.listEnabledServers());
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取 MCP 服务器详情")
    public ResponseEntity<McpServer> getServer(
            @Parameter(description = "服务器ID")
            @PathVariable Long id) {
        McpServer server = mcpServerService.getServer(id);
        if (server == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(server);
    }

    @GetMapping("/{id}/tools")
    @Operation(summary = "获取 MCP 服务器工具列表")
    public ResponseEntity<List<Map<String, Object>>> getServerTools(
            @Parameter(description = "服务器ID")
            @PathVariable Long id) {
        List<Tool> tools = mcpToolRegistry.getToolsForServer(id);
        List<Map<String, Object>> result = tools.stream().map(tool -> {
            Map<String, Object> toolMap = new HashMap<>();
            toolMap.put("name", tool.getName());
            toolMap.put("description", tool.getDescription());
            toolMap.put("inputSchema", tool.getInputSchema());
            return toolMap;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "获取 MCP 服务器连接状态")
    public ResponseEntity<Map<String, Object>> getServerStatus(
            @Parameter(description = "服务器ID")
            @PathVariable Long id) {
        var client = mcpClientManager.getClient(id);
        Map<String, Object> status = new HashMap<>();
        status.put("connected", client != null && client.isConnected());
        status.put("hasTools", mcpToolRegistry.hasTools());
        return ResponseEntity.ok(status);
    }

    @PostMapping
    @Operation(summary = "添加 MCP 服务器")
    public ResponseEntity<McpServer> addServer(
            @Parameter(description = "MCP 服务器信息")
            @RequestBody McpServer server) {
        return ResponseEntity.ok(mcpServerService.addServer(server));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新 MCP 服务器")
    public ResponseEntity<McpServer> updateServer(
            @Parameter(description = "服务器ID")
            @PathVariable Long id,
            @Parameter(description = "MCP 服务器信息")
            @RequestBody McpServer server) {
        server.setId(id);
        boolean success = mcpServerService.updateById(server);
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(mcpServerService.getServer(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除 MCP 服务器")
    public ResponseEntity<Void> deleteServer(
            @Parameter(description = "服务器ID")
            @PathVariable Long id) {
        boolean success = mcpServerService.deleteServer(id);
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/enable")
    @Operation(summary = "启用 MCP 服务器")
    public ResponseEntity<Map<String, Object>> enableServer(
            @Parameter(description = "服务器ID")
            @PathVariable Long id) {
        boolean success = mcpServerService.enableServer(id);
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "MCP 服务器已启用"));
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "禁用 MCP 服务器")
    public ResponseEntity<Map<String, Object>> disableServer(
            @Parameter(description = "服务器ID")
            @PathVariable Long id) {
        boolean success = mcpServerService.disableServer(id);
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "MCP 服务器已禁用"));
    }

    @PostMapping("/{id}/reconnect")
    @Operation(summary = "重新连接 MCP 服务器")
    public ResponseEntity<Map<String, Object>> reconnectServer(
            @Parameter(description = "服务器ID")
            @PathVariable Long id) {
        try {
            mcpClientManager.reconnectClient(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "MCP 服务器正在重连"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "重连失败: " + e.getMessage()));
        }
    }

    @PostMapping("/reconnect-all")
    @Operation(summary = "重新连接所有 MCP 服务器")
    public ResponseEntity<Map<String, Object>> reconnectAllServers() {
        try {
            List<McpServer> servers = mcpServerService.listEnabledServers();
            for (McpServer server : servers) {
                try {
                    mcpClientManager.reconnectClient(server.getId());
                } catch (Exception e) {
                    // 单个服务器重连失败不影响其他
                }
            }
            return ResponseEntity.ok(Map.of("success", true, "message", "所有 MCP 服务器正在重连"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "重连失败: " + e.getMessage()));
        }
    }
}