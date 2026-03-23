package com.wk.agent.core;

import lombok.Data;

import java.util.Map;

@Data
public class AgentTask {
    private String taskId;
    private String taskType;
    private String taskContent;
    private String agentId;
    private String sessionId;
    private long createdAt;
    private Map<String, Object> parameters;
    
    public AgentTask() {
        this.createdAt = System.currentTimeMillis();
    }
    
    public AgentTask(String taskType, String taskContent) {
        this();
        this.taskType = taskType;
        this.taskContent = taskContent;
    }
    
    public AgentTask(String taskId, String taskType, String taskContent, String sessionId) {
        this(taskType, taskContent);
        this.taskId = taskId;
        this.sessionId = sessionId;
    }
}
