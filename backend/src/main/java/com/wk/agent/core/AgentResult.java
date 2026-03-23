package com.wk.agent.core;

import java.util.Map;

public class AgentResult {
    private String resultId;
    private String agentId;
    private String taskId;
    private boolean success;
    private String message;
    private Object data;
    private long completedAt;
    private Map<String, Object> metadata;
    
    public AgentResult() {
        this.completedAt = System.currentTimeMillis();
    }
    
    public AgentResult(String message, boolean success) {
        this();
        this.message = message;
        this.success = success;
    }
    
    public AgentResult(String message, boolean success, Object data) {
        this(message, success);
        this.data = data;
    }
    
    public String getResultId() {
        return resultId;
    }
    
    public void setResultId(String resultId) {
        this.resultId = resultId;
    }
    
    public String getAgentId() {
        return agentId;
    }
    
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }
    
    public String getTaskId() {
        return taskId;
    }
    
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public Object getData() {
        return data;
    }
    
    public void setData(Object data) {
        this.data = data;
    }
    
    public long getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
