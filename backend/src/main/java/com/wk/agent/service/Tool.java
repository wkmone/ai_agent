package com.wk.agent.service;

import java.util.Map;

/**
 * 工具接口
 * 
 * 注意：新的工具应该使用Spring AI的@Tool注解
 * 这个接口保留用于兼容旧的代码
 */
public interface Tool {
    
    /**
     * 获取工具名称
     */
    String getName();
    
    /**
     * 获取工具描述
     */
    String getDescription();
    
    /**
     * 执行工具
     */
    String execute(Map<String, Object> params);
}
