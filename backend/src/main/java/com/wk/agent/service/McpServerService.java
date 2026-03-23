package com.wk.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wk.agent.entity.McpServer;
import com.wk.agent.mapper.McpServerMapper;
import com.wk.agent.mcp.McpClientManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class McpServerService extends ServiceImpl<McpServerMapper, McpServer> {

    @Autowired
    private McpClientManager mcpClientManager;

    public List<McpServer> listAllServers() {
        return list(new LambdaQueryWrapper<McpServer>().orderByDesc(McpServer::getCreatedAt));
    }

    public List<McpServer> listEnabledServers() {
        return list(new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getEnabled, true)
                .orderByDesc(McpServer::getCreatedAt));
    }

    public McpServer addServer(McpServer server) {
        server.setCreatedAt(LocalDateTime.now());
        server.setUpdatedAt(LocalDateTime.now());
        if (server.getEnabled() == null) {
            server.setEnabled(true);
        }
        save(server);
        log.info("添加 MCP 服务器成功: id={}, name={}", server.getId(), server.getName());

        if (Boolean.TRUE.equals(server.getEnabled())) {
            initializeClient(server);
        }

        return server;
    }

    public boolean deleteServer(Long id) {
        McpServer server = getById(id);
        if (server != null) {
            destroyClient(id);
        }
        boolean success = removeById(id);
        if (success) {
            log.info("删除 MCP 服务器成功: id={}", id);
        }
        return success;
    }

    public boolean enableServer(Long id) {
        McpServer server = getById(id);
        if (server != null) {
            server.setEnabled(true);
            server.setUpdatedAt(LocalDateTime.now());
            boolean success = updateById(server);
            if (success) {
                log.info("启用 MCP 服务器成功: id={}", id);
                initializeClient(server);
            }
            return success;
        }
        return false;
    }

    public boolean disableServer(Long id) {
        McpServer server = getById(id);
        if (server != null) {
            server.setEnabled(false);
            server.setUpdatedAt(LocalDateTime.now());
            boolean success = updateById(server);
            if (success) {
                log.info("禁用 MCP 服务器成功: id={}", id);
                destroyClient(id);
            }
            return success;
        }
        return false;
    }

    public McpServer getServer(Long id) {
        return getById(id);
    }

    private void initializeClient(McpServer server) {
        try {
            mcpClientManager.initializeClient(server);
        } catch (Exception e) {
            log.error("初始化 MCP Client 失败: name={}", server.getName(), e);
        }
    }

    private void destroyClient(Long id) {
        try {
            mcpClientManager.destroyClient(id);
        } catch (Exception e) {
            log.error("销毁 MCP Client 失败: id={}", id, e);
        }
    }
}