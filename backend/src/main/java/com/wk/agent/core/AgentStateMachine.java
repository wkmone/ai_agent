package com.wk.agent.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class AgentStateMachine {

    private static final Logger log = LoggerFactory.getLogger(AgentStateMachine.class);

    private final String agentId;
    private AgentStatus currentStatus;
    private final Map<AgentStatus, Set<AgentStatus>> allowedTransitions;
    private final List<StatusChangeListener> listeners;
    private int errorCount;
    private long lastStatusChangeTime;
    private long lastHealthyTime;
    private String lastError;

    public AgentStateMachine(String agentId) {
        this.agentId = agentId;
        this.currentStatus = AgentStatus.INITIALIZED;
        this.listeners = new ArrayList<>();
        this.errorCount = 0;
        this.lastStatusChangeTime = System.currentTimeMillis();
        this.lastHealthyTime = System.currentTimeMillis();
        this.allowedTransitions = new EnumMap<>(AgentStatus.class);
        
        initializeAllowedTransitions();
    }

    private void initializeAllowedTransitions() {
        allowedTransitions.put(AgentStatus.INITIALIZED, 
            EnumSet.of(AgentStatus.READY, AgentStatus.RUNNING, AgentStatus.ERROR));
        allowedTransitions.put(AgentStatus.READY, 
            EnumSet.of(AgentStatus.RUNNING, AgentStatus.PAUSED, AgentStatus.ERROR));
        allowedTransitions.put(AgentStatus.RUNNING, 
            EnumSet.of(AgentStatus.READY, AgentStatus.PAUSED, AgentStatus.COMPLETED, AgentStatus.ERROR));
        allowedTransitions.put(AgentStatus.PAUSED, 
            EnumSet.of(AgentStatus.READY, AgentStatus.RUNNING, AgentStatus.ERROR));
        allowedTransitions.put(AgentStatus.ERROR, 
            EnumSet.of(AgentStatus.READY, AgentStatus.RUNNING, AgentStatus.RECOVERING));
        allowedTransitions.put(AgentStatus.COMPLETED, 
            EnumSet.of(AgentStatus.READY, AgentStatus.RUNNING));
        allowedTransitions.put(AgentStatus.RECOVERING, 
            EnumSet.of(AgentStatus.READY, AgentStatus.RUNNING, AgentStatus.ERROR));
    }

    public synchronized boolean transitionTo(AgentStatus newStatus) {
        return transitionTo(newStatus, null);
    }

    public synchronized boolean transitionTo(AgentStatus newStatus, String reason) {
        Set<AgentStatus> allowed = allowedTransitions.get(currentStatus);
        
        if (allowed == null || !allowed.contains(newStatus)) {
            log.warn("Agent {} 状态转换不允许: {} -> {}", agentId, currentStatus, newStatus);
            return false;
        }
        
        AgentStatus oldStatus = currentStatus;
        currentStatus = newStatus;
        lastStatusChangeTime = System.currentTimeMillis();
        
        if (newStatus != AgentStatus.ERROR && newStatus != AgentStatus.RECOVERING) {
            lastHealthyTime = System.currentTimeMillis();
            if (errorCount > 0) {
                log.info("Agent {} 恢复正常，重置错误计数: {}", agentId, errorCount);
                errorCount = 0;
            }
        }
        
        log.info("Agent {} 状态转换: {} -> {} {}", agentId, oldStatus, newStatus, 
                reason != null ? "(" + reason + ")" : "");
        
        notifyListeners(oldStatus, newStatus, reason);
        
        return true;
    }

    public synchronized boolean transitionToError(String error) {
        errorCount++;
        lastError = error;
        
        log.error("Agent {} 进入错误状态 (错误计数: {}): {}", agentId, errorCount, error);
        
        return transitionTo(AgentStatus.ERROR, error);
    }

    public synchronized boolean canRecover() {
        return errorCount < getMaxErrorCount();
    }

    public synchronized boolean startRecovery() {
        if (!canRecover()) {
            log.warn("Agent {} 错误次数过多，无法恢复", agentId);
            return false;
        }
        
        return transitionTo(AgentStatus.RECOVERING, "开始恢复");
    }

    public synchronized boolean completeRecovery() {
        if (currentStatus != AgentStatus.RECOVERING) {
            return false;
        }
        
        return transitionTo(AgentStatus.READY, "恢复完成");
    }

    public synchronized void forceStatus(AgentStatus status) {
        AgentStatus oldStatus = currentStatus;
        currentStatus = status;
        lastStatusChangeTime = System.currentTimeMillis();
        
        if (status != AgentStatus.ERROR) {
            lastHealthyTime = System.currentTimeMillis();
        }
        
        log.warn("Agent {} 强制状态变更: {} -> {}", agentId, oldStatus, status);
        notifyListeners(oldStatus, status, "强制变更");
    }

    public void addListener(StatusChangeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(StatusChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(AgentStatus oldStatus, AgentStatus newStatus, String reason) {
        for (StatusChangeListener listener : listeners) {
            try {
                listener.onStatusChange(agentId, oldStatus, newStatus, reason);
            } catch (Exception e) {
                log.error("状态变更监听器执行失败", e);
            }
        }
    }

    public AgentStatus getCurrentStatus() {
        return currentStatus;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public String getLastError() {
        return lastError;
    }

    public long getLastStatusChangeTime() {
        return lastStatusChangeTime;
    }

    public long getLastHealthyTime() {
        return lastHealthyTime;
    }

    public long getTimeSinceLastHealthy() {
        return System.currentTimeMillis() - lastHealthyTime;
    }

    public long getTimeInCurrentStatus() {
        return System.currentTimeMillis() - lastStatusChangeTime;
    }

    protected int getMaxErrorCount() {
        return 3;
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("agentId", agentId);
        stats.put("currentStatus", currentStatus.name());
        stats.put("errorCount", errorCount);
        stats.put("lastError", lastError);
        stats.put("timeInCurrentStatusMs", getTimeInCurrentStatus());
        stats.put("timeSinceLastHealthyMs", getTimeSinceLastHealthy());
        stats.put("canRecover", canRecover());
        return stats;
    }

    @FunctionalInterface
    public interface StatusChangeListener {
        void onStatusChange(String agentId, AgentStatus oldStatus, AgentStatus newStatus, String reason);
    }
}
