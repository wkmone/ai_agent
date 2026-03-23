package com.wk.agent.orchestration;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class OrchestrationResult {

    private String resultId;
    private String taskId;
    private String sessionId;
    private boolean success;
    private String result;
    private String error;
    private List<AgentExecution> agentExecutions;
    private Map<String, Object> metadata;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long durationMs;

    @Data
    public static class AgentExecution {
        private String agentId;
        private String agentName;
        private String action;
        private boolean success;
        private String output;
        private String error;
        private long durationMs;
        private LocalDateTime timestamp;

        public static AgentExecution of(String agentId, String agentName, String action) {
            AgentExecution execution = new AgentExecution();
            execution.setAgentId(agentId);
            execution.setAgentName(agentName);
            execution.setAction(action);
            execution.setTimestamp(LocalDateTime.now());
            return execution;
        }
    }

    public OrchestrationResult() {
        this.resultId = java.util.UUID.randomUUID().toString();
        this.agentExecutions = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.startTime = LocalDateTime.now();
    }

    public static OrchestrationResult success(String taskId, String result) {
        OrchestrationResult r = new OrchestrationResult();
        r.setTaskId(taskId);
        r.setSuccess(true);
        r.setResult(result);
        r.setEndTime(LocalDateTime.now());
        return r;
    }

    public static OrchestrationResult failure(String taskId, String error) {
        OrchestrationResult r = new OrchestrationResult();
        r.setTaskId(taskId);
        r.setSuccess(false);
        r.setError(error);
        r.setEndTime(LocalDateTime.now());
        return r;
    }

    public void addExecution(AgentExecution execution) {
        if (this.agentExecutions == null) {
            this.agentExecutions = new ArrayList<>();
        }
        this.agentExecutions.add(execution);
    }

    public void addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    public long calculateDuration() {
        if (startTime != null && endTime != null) {
            this.durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        }
        return this.durationMs;
    }

    public int getExecutionCount() {
        return agentExecutions != null ? agentExecutions.size() : 0;
    }

    public long getTotalAgentDuration() {
        if (agentExecutions == null) return 0;
        return agentExecutions.stream()
                .mapToLong(e -> e.getDurationMs() > 0 ? e.getDurationMs() : 0)
                .sum();
    }
}
