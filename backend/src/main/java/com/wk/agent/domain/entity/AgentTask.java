package com.wk.agent.domain.entity;

import java.time.LocalDateTime;

public class AgentTask {
    private String taskId;
    private String taskType;
    private String taskContent;
    private LocalDateTime createdAt;
    private LocalDateTime executedAt;
    private String status;

    public AgentTask(String taskType, String taskContent) {
        this.taskId = java.util.UUID.randomUUID().toString();
        this.taskType = taskType;
        this.taskContent = taskContent;
        this.createdAt = LocalDateTime.now();
        this.status = "PENDING";
    }

    public String getTaskId() {
        return taskId;
    }

    public String getTaskType() {
        return taskType;
    }

    public String getTaskContent() {
        return taskContent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }
}