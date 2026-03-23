package com.wk.agent.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentSharedStateService {

    private static final Logger log = LoggerFactory.getLogger(AgentSharedStateService.class);

    @Value("${agent.shared-state.max-states-per-task:100}")
    private int maxStatesPerTask;

    @Value("${agent.shared-state.max-tasks:1000}")
    private int maxTasks;

    @Value("${agent.shared-state.ttl-hours:24}")
    private int ttlHours;

    private final Map<String, TaskSharedState> taskStates = new ConcurrentHashMap<>();
    private final Map<String, GlobalSharedState> globalStates = new ConcurrentHashMap<>();
    private final List<StateChangeListener> listeners = new ArrayList<>();

    public static class SharedStateEntry {
        private final String key;
        private Object value;
        private String sourceAgentId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private int accessCount;
        private Map<String, Object> metadata;

        public SharedStateEntry(String key, Object value, String sourceAgentId) {
            this.key = key;
            this.value = value;
            this.sourceAgentId = sourceAgentId;
            this.createdAt = LocalDateTime.now();
            this.updatedAt = LocalDateTime.now();
            this.accessCount = 0;
            this.metadata = new HashMap<>();
        }

        public void updateValue(Object newValue, String agentId) {
            this.value = newValue;
            this.sourceAgentId = agentId;
            this.updatedAt = LocalDateTime.now();
        }

        public void incrementAccess() { this.accessCount++; }

        public String getKey() { return key; }
        public Object getValue() { return value; }
        public String getSourceAgentId() { return sourceAgentId; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public int getAccessCount() { return accessCount; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(String metaKey, Object metaValue) { this.metadata.put(metaKey, metaValue); }
    }

    public static class TaskSharedState {
        private final String taskId;
        private final String sessionId;
        private final Map<String, SharedStateEntry> states;
        private final List<String> participatingAgents;
        private final LocalDateTime createdAt;
        private LocalDateTime lastAccessedAt;
        private String currentPhase;
        private Map<String, Object> taskContext;

        public TaskSharedState(String taskId, String sessionId) {
            this.taskId = taskId;
            this.sessionId = sessionId;
            this.states = new ConcurrentHashMap<>();
            this.participatingAgents = new ArrayList<>();
            this.createdAt = LocalDateTime.now();
            this.lastAccessedAt = LocalDateTime.now();
            this.taskContext = new HashMap<>();
        }

        public void addParticipatingAgent(String agentId) {
            if (!participatingAgents.contains(agentId)) {
                participatingAgents.add(agentId);
            }
        }

        public String getTaskId() { return taskId; }
        public String getSessionId() { return sessionId; }
        public Map<String, SharedStateEntry> getStates() { return states; }
        public List<String> getParticipatingAgents() { return participatingAgents; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getLastAccessedAt() { return lastAccessedAt; }
        public void setLastAccessedAt(LocalDateTime time) { this.lastAccessedAt = time; }
        public String getCurrentPhase() { return currentPhase; }
        public void setCurrentPhase(String phase) { this.currentPhase = phase; }
        public Map<String, Object> getTaskContext() { return taskContext; }
        public void setTaskContext(String key, Object value) { this.taskContext.put(key, value); }
    }

    public static class GlobalSharedState {
        private final String namespace;
        private final Map<String, SharedStateEntry> states;
        private final LocalDateTime createdAt;

        public GlobalSharedState(String namespace) {
            this.namespace = namespace;
            this.states = new ConcurrentHashMap<>();
            this.createdAt = LocalDateTime.now();
        }

        public String getNamespace() { return namespace; }
        public Map<String, SharedStateEntry> getStates() { return states; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }

    public String createTaskState(String sessionId) {
        String taskId = "task_" + UUID.randomUUID().toString().substring(0, 8);
        TaskSharedState state = new TaskSharedState(taskId, sessionId);
        taskStates.put(taskId, state);
        
        log.info("创建任务共享状态: taskId={}, sessionId={}", taskId, sessionId);
        notifyListeners(taskId, StateChangeType.TASK_CREATED, null, null);
        
        return taskId;
    }

    public void setState(String taskId, String key, Object value, String agentId) {
        TaskSharedState taskState = taskStates.get(taskId);
        if (taskState == null) {
            log.warn("任务状态不存在: {}", taskId);
            return;
        }

        SharedStateEntry entry = taskState.getStates().get(key);
        if (entry == null) {
            entry = new SharedStateEntry(key, value, agentId);
            taskState.getStates().put(key, entry);
        } else {
            entry.updateValue(value, agentId);
        }

        taskState.addParticipatingAgent(agentId);
        taskState.setLastAccessedAt(LocalDateTime.now());

        log.debug("设置任务状态: taskId={}, key={}, agentId={}", taskId, key, agentId);
        notifyListeners(taskId, StateChangeType.STATE_SET, key, value);
    }

    public Object getState(String taskId, String key) {
        TaskSharedState taskState = taskStates.get(taskId);
        if (taskState == null) {
            return null;
        }

        SharedStateEntry entry = taskState.getStates().get(key);
        if (entry == null) {
            return null;
        }

        entry.incrementAccess();
        taskState.setLastAccessedAt(LocalDateTime.now());
        
        return entry.getValue();
    }

    public <T> T getState(String taskId, String key, Class<T> type) {
        Object value = getState(taskId, key);
        if (value != null && type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }

    public Map<String, Object> getAllStates(String taskId) {
        TaskSharedState taskState = taskStates.get(taskId);
        if (taskState == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, SharedStateEntry> entry : taskState.getStates().entrySet()) {
            result.put(entry.getKey(), entry.getValue().getValue());
        }
        
        return result;
    }

    public void removeState(String taskId, String key) {
        TaskSharedState taskState = taskStates.get(taskId);
        if (taskState != null) {
            taskState.getStates().remove(key);
            notifyListeners(taskId, StateChangeType.STATE_REMOVED, key, null);
        }
    }

    public void setGlobalState(String namespace, String key, Object value, String agentId) {
        GlobalSharedState globalState = globalStates.computeIfAbsent(namespace, GlobalSharedState::new);
        
        SharedStateEntry entry = globalState.getStates().get(key);
        if (entry == null) {
            entry = new SharedStateEntry(key, value, agentId);
            globalState.getStates().put(key, entry);
        } else {
            entry.updateValue(value, agentId);
        }

        log.debug("设置全局状态: namespace={}, key={}, agentId={}", namespace, key, agentId);
        notifyListeners(namespace, StateChangeType.GLOBAL_STATE_SET, key, value);
    }

    public Object getGlobalState(String namespace, String key) {
        GlobalSharedState globalState = globalStates.get(namespace);
        if (globalState == null) {
            return null;
        }

        SharedStateEntry entry = globalState.getStates().get(key);
        if (entry == null) {
            return null;
        }

        entry.incrementAccess();
        return entry.getValue();
    }

    public void setTaskContext(String taskId, String key, Object value) {
        TaskSharedState taskState = taskStates.get(taskId);
        if (taskState != null) {
            taskState.setTaskContext(key, value);
        }
    }

    public Object getTaskContext(String taskId, String key) {
        TaskSharedState taskState = taskStates.get(taskId);
        if (taskState == null) {
            return null;
        }
        return taskState.getTaskContext().get(key);
    }

    public void setCurrentPhase(String taskId, String phase) {
        TaskSharedState taskState = taskStates.get(taskId);
        if (taskState != null) {
            taskState.setCurrentPhase(phase);
            notifyListeners(taskId, StateChangeType.PHASE_CHANGED, "phase", phase);
        }
    }

    public String getCurrentPhase(String taskId) {
        TaskSharedState taskState = taskStates.get(taskId);
        return taskState != null ? taskState.getCurrentPhase() : null;
    }

    public List<String> getParticipatingAgents(String taskId) {
        TaskSharedState taskState = taskStates.get(taskId);
        return taskState != null ? new ArrayList<>(taskState.getParticipatingAgents()) : Collections.emptyList();
    }

    public void completeTask(String taskId) {
        TaskSharedState taskState = taskStates.get(taskId);
        if (taskState != null) {
            notifyListeners(taskId, StateChangeType.TASK_COMPLETED, null, null);
        }
    }

    public void cleanupTask(String taskId) {
        taskStates.remove(taskId);
        log.info("清理任务状态: {}", taskId);
        notifyListeners(taskId, StateChangeType.TASK_CLEANED, null, null);
    }

    @Scheduled(fixedRate = 3600000)
    public void cleanupExpiredStates() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(ttlHours);
        int cleaned = 0;

        Iterator<Map.Entry<String, TaskSharedState>> iterator = taskStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, TaskSharedState> entry = iterator.next();
            if (entry.getValue().getLastAccessedAt().isBefore(cutoff)) {
                iterator.remove();
                cleaned++;
            }
        }

        if (cleaned > 0) {
            log.info("清理过期任务状态: {} 个", cleaned);
        }
    }

    public void addListener(StateChangeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(StateChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(String taskId, StateChangeType type, String key, Object value) {
        for (StateChangeListener listener : listeners) {
            try {
                listener.onStateChange(taskId, type, key, value);
            } catch (Exception e) {
                log.error("状态变更监听器执行失败", e);
            }
        }
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        
        int totalTasks = taskStates.size();
        int totalStates = 0;
        int totalAgents = 0;

        for (TaskSharedState taskState : taskStates.values()) {
            totalStates += taskState.getStates().size();
            totalAgents += taskState.getParticipatingAgents().size();
        }

        stats.put("totalTasks", totalTasks);
        stats.put("totalStates", totalStates);
        stats.put("totalAgents", totalAgents);
        stats.put("globalNamespaces", globalStates.size());
        stats.put("maxStatesPerTask", maxStatesPerTask);
        stats.put("maxTasks", maxTasks);
        stats.put("ttlHours", ttlHours);

        return stats;
    }

    public enum StateChangeType {
        TASK_CREATED,
        TASK_COMPLETED,
        TASK_CLEANED,
        STATE_SET,
        STATE_REMOVED,
        GLOBAL_STATE_SET,
        PHASE_CHANGED
    }

    @FunctionalInterface
    public interface StateChangeListener {
        void onStateChange(String taskId, StateChangeType type, String key, Object value);
    }
}
