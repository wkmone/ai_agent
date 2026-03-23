package com.wk.agent.orchestration;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
public class OrchestrationTask {

    private String taskId;
    private String sessionId;
    private String description;
    private TaskType taskType;
    private TaskPriority priority;
    private TaskStatus status;
    private String assignedAgentId;
    private Map<String, Object> parameters;
    private Map<String, Object> context;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private int retryCount;
    private int maxRetries;

    public enum TaskType {
        ANALYSIS,
        PLANNING,
        EXECUTION,
        VERIFICATION,
        RESEARCH,
        CREATION,
        GENERAL
    }

    public enum TaskPriority {
        LOW(1),
        MEDIUM(2),
        HIGH(3),
        CRITICAL(4);

        private final int value;

        TaskPriority(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum TaskStatus {
        PENDING,
        ASSIGNED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public OrchestrationTask() {
        this.parameters = new HashMap<>();
        this.context = new HashMap<>();
        this.status = TaskStatus.PENDING;
        this.priority = TaskPriority.MEDIUM;
        this.retryCount = 0;
        this.maxRetries = 3;
        this.createdAt = LocalDateTime.now();
    }

    public static OrchestrationTask create(String description) {
        OrchestrationTask task = new OrchestrationTask();
        task.setTaskId(java.util.UUID.randomUUID().toString());
        task.setDescription(description);
        return task;
    }

    public static OrchestrationTask create(String description, TaskType type) {
        OrchestrationTask task = create(description);
        task.setTaskType(type);
        return task;
    }

    public static OrchestrationTask create(String description, TaskType type, TaskPriority priority) {
        OrchestrationTask task = create(description, type);
        task.setPriority(priority);
        return task;
    }

    public void addParameter(String key, Object value) {
        if (this.parameters == null) {
            this.parameters = new HashMap<>();
        }
        this.parameters.put(key, value);
    }

    public void addContext(String key, Object value) {
        if (this.context == null) {
            this.context = new HashMap<>();
        }
        this.context.put(key, value);
    }

    public void markRunning() {
        this.status = TaskStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public void markCompleted() {
        this.status = TaskStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = TaskStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }

    public boolean canRetry() {
        return retryCount < maxRetries;
    }

    public void incrementRetry() {
        this.retryCount++;
        this.status = TaskStatus.PENDING;
    }
}
