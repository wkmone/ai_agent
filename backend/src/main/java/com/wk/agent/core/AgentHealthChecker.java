package com.wk.agent.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(AgentHealthChecker.class);

    @Value("${agent.health.check-interval:30000}")
    private long checkIntervalMs;

    @Value("${agent.health.timeout:60000}")
    private long healthTimeoutMs;

    @Value("${agent.health.max-unhealthy-time:300000}")
    private long maxUnhealthyTimeMs;

    @Value("${agent.health.auto-recovery:true}")
    private boolean autoRecoveryEnabled;

    @Autowired(required = false)
    private List<AbstractAgent> agents;

    private final Map<String, AgentHealthRecord> healthRecords = new ConcurrentHashMap<>();
    private final List<HealthCheckListener> listeners = new ArrayList<>();

    @Scheduled(fixedDelayString = "${agent.health.check-interval:30000}")
    public void performHealthCheck() {
        if (agents == null || agents.isEmpty()) {
            return;
        }

        log.debug("开始 Agent 健康检查，共 {} 个 Agent", agents.size());

        for (AbstractAgent agent : agents) {
            try {
                checkAgentHealth(agent);
            } catch (Exception e) {
                log.error("Agent {} 健康检查失败", agent.getId(), e);
            }
        }
    }

    private void checkAgentHealth(AbstractAgent agent) {
        String agentId = agent.getId();
        AgentStatus status = agent.getStatus();
        AgentHealthRecord record = healthRecords.computeIfAbsent(agentId, 
                k -> new AgentHealthRecord(agentId));

        record.setLastCheckTime(LocalDateTime.now());
        record.setCurrentStatus(status);

        boolean isHealthy = evaluateHealth(agent, record);
        record.setHealthy(isHealthy);

        if (!isHealthy) {
            handleUnhealthyAgent(agent, record);
        } else {
            record.setConsecutiveFailures(0);
            record.setLastHealthyTime(LocalDateTime.now());
        }

        notifyListeners(agentId, record);
    }

    private boolean evaluateHealth(AbstractAgent agent, AgentHealthRecord record) {
        AgentStatus status = agent.getStatus();

        if (status == AgentStatus.ERROR) {
            return false;
        }

        if (status == AgentStatus.RECOVERING) {
            long timeInRecovery = record.getTimeSinceStatusChange();
            if (timeInRecovery > maxUnhealthyTimeMs) {
                log.warn("Agent {} 恢复超时", agent.getId());
                return false;
            }
            return true;
        }

        if (status == AgentStatus.RUNNING) {
            long runningTime = record.getTimeInCurrentStatus();
            if (runningTime > healthTimeoutMs * 2) {
                log.warn("Agent {} 运行时间过长: {}ms", agent.getId(), runningTime);
                return false;
            }
        }

        return true;
    }

    private void handleUnhealthyAgent(AbstractAgent agent, AgentHealthRecord record) {
        record.incrementConsecutiveFailures();
        int failures = record.getConsecutiveFailures();

        log.warn("Agent {} 不健康 (连续失败: {})", agent.getId(), failures);

        if (autoRecoveryEnabled && failures >= 2) {
            attemptRecovery(agent, record);
        }

        if (failures >= 5) {
            log.error("Agent {} 连续失败次数过多，需要人工干预", agent.getId());
        }
    }

    private void attemptRecovery(AbstractAgent agent, AgentHealthRecord record) {
        String agentId = agent.getId();
        
        log.info("尝试恢复 Agent: {}", agentId);

        try {
            if (agent.getStatus() == AgentStatus.ERROR) {
                agent.initialize();
                record.setRecoveryAttempts(record.getRecoveryAttempts() + 1);
                record.setLastRecoveryTime(LocalDateTime.now());
                log.info("Agent {} 恢复尝试完成", agentId);
            }
        } catch (Exception e) {
            log.error("Agent {} 恢复失败", agentId, e);
            record.setLastError(e.getMessage());
        }
    }

    public void addListener(HealthCheckListener listener) {
        listeners.add(listener);
    }

    public void removeListener(HealthCheckListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(String agentId, AgentHealthRecord record) {
        for (HealthCheckListener listener : listeners) {
            try {
                listener.onHealthCheck(agentId, record);
            } catch (Exception e) {
                log.error("健康检查监听器执行失败", e);
            }
        }
    }

    public AgentHealthRecord getHealthRecord(String agentId) {
        return healthRecords.get(agentId);
    }

    public Map<String, AgentHealthRecord> getAllHealthRecords() {
        return new HashMap<>(healthRecords);
    }

    public Map<String, Object> getHealthSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        
        int total = healthRecords.size();
        int healthy = 0;
        int unhealthy = 0;
        int recovering = 0;

        for (AgentHealthRecord record : healthRecords.values()) {
            if (record.isHealthy()) {
                healthy++;
            } else {
                unhealthy++;
            }
            if (record.getCurrentStatus() == AgentStatus.RECOVERING) {
                recovering++;
            }
        }

        summary.put("totalAgents", total);
        summary.put("healthyCount", healthy);
        summary.put("unhealthyCount", unhealthy);
        summary.put("recoveringCount", recovering);
        summary.put("autoRecoveryEnabled", autoRecoveryEnabled);
        summary.put("checkIntervalMs", checkIntervalMs);
        summary.put("healthTimeoutMs", healthTimeoutMs);

        return summary;
    }

    public void forceHealthCheck(String agentId) {
        if (agents == null) return;
        
        for (AbstractAgent agent : agents) {
            if (agent.getId().equals(agentId)) {
                checkAgentHealth(agent);
                break;
            }
        }
    }

    public static class AgentHealthRecord {
        private final String agentId;
        private LocalDateTime lastCheckTime;
        private LocalDateTime lastHealthyTime;
        private LocalDateTime lastRecoveryTime;
        private AgentStatus currentStatus;
        private boolean healthy;
        private int consecutiveFailures;
        private int recoveryAttempts;
        private String lastError;
        private long statusChangeTime;

        public AgentHealthRecord(String agentId) {
            this.agentId = agentId;
            this.statusChangeTime = System.currentTimeMillis();
        }

        public long getTimeInCurrentStatus() {
            return System.currentTimeMillis() - statusChangeTime;
        }

        public long getTimeSinceStatusChange() {
            return System.currentTimeMillis() - statusChangeTime;
        }

        public String getAgentId() { return agentId; }
        public LocalDateTime getLastCheckTime() { return lastCheckTime; }
        public void setLastCheckTime(LocalDateTime lastCheckTime) { this.lastCheckTime = lastCheckTime; }
        public LocalDateTime getLastHealthyTime() { return lastHealthyTime; }
        public void setLastHealthyTime(LocalDateTime lastHealthyTime) { this.lastHealthyTime = lastHealthyTime; }
        public LocalDateTime getLastRecoveryTime() { return lastRecoveryTime; }
        public void setLastRecoveryTime(LocalDateTime lastRecoveryTime) { this.lastRecoveryTime = lastRecoveryTime; }
        public AgentStatus getCurrentStatus() { return currentStatus; }
        public void setCurrentStatus(AgentStatus currentStatus) { 
            this.currentStatus = currentStatus;
            this.statusChangeTime = System.currentTimeMillis();
        }
        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }
        public int getConsecutiveFailures() { return consecutiveFailures; }
        public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }
        public void incrementConsecutiveFailures() { this.consecutiveFailures++; }
        public int getRecoveryAttempts() { return recoveryAttempts; }
        public void setRecoveryAttempts(int recoveryAttempts) { this.recoveryAttempts = recoveryAttempts; }
        public String getLastError() { return lastError; }
        public void setLastError(String lastError) { this.lastError = lastError; }
    }

    @FunctionalInterface
    public interface HealthCheckListener {
        void onHealthCheck(String agentId, AgentHealthRecord record);
    }
}
