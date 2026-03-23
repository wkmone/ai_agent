package com.wk.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心
 * 
 * 注意：使用Spring AI的@Tool注解的工具不需要在这里注册
 * Spring AI会自动扫描所有带有@Tool注解的方法
 * 
 * 这个类保留作为兼容层，用于支持旧的代码
 */
@Service
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);
    
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    public ToolRegistry() {
        log.info("ToolRegistry 初始化完成");
        log.info("注意：使用@Tool注解的工具由Spring AI自动管理，无需手动注册");
    }

    /**
     * 初始化默认工具
     * 现在是一个空实现，因为所有工具都使用@Tool注解
     */
    public void initializeDefaultTools() {
        log.info("initializeDefaultTools() 已弃用，所有工具使用@Tool注解自动管理");
    }

    /**
     * 注册工具
     */
    public void registerTool(Tool tool) {
        tools.put(tool.getName(), tool);
        log.info("注册工具: {} - {}", tool.getName(), tool.getDescription());
    }

    /**
     * 执行工具
     */
    public String executeTool(String name, Map<String, Object> params) {
        Tool tool = tools.get(name);
        if (tool == null) {
            return "工具不存在: " + name;
        }
        return tool.execute(params);
    }

    /**
     * 获取工具描述
     */
    public String getToolDescriptions() {
        StringBuilder sb = new StringBuilder();
        sb.append("可用工具:\n");
        for (Tool tool : tools.values()) {
            sb.append("- ").append(tool.getName()).append(": ").append(tool.getDescription()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 获取工具
     */
    public Tool getTool(String name) {
        return tools.get(name);
    }

    /**
     * 获取所有工具
     */
    public Map<String, Tool> getAllTools() {
        return new ConcurrentHashMap<>(tools);
    }

    /**
     * 检查工具是否存在
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    /**
     * 移除工具
     */
    public void removeTool(String name) {
        tools.remove(name);
        log.info("移除工具: {}", name);
    }

    /**
     * 清空所有工具
     */
    public void clearTools() {
        tools.clear();
        log.info("清空所有工具");
    }
}
