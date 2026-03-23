package com.wk.agent.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(AgentRecoveryService.class);

    @Value("${agent.recovery.max-attempts:3}")
    private int maxRecoveryAttempts;

    @Value("${agent.recovery.delay-ms:5000}")
    private long recoveryDelayMs;

    @Value("${agent.recovery.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    @Autowired(required = false)
    private List<AbstractAgent> agents;

    private final Map<String, RecoveryContext> recoveryContexts = new ConcurrentHashMap<>();
    private final List<RecoveryListener> listeners = new ArrayList<>();

    public RecoveryResult attemptRecovery(String agentId) {
        AbstractAgent agent = findAgent(agentId);
        if (agent == null) {
            return RecoveryResult.failure("Agent not found: " + agentId);
        }

        RecoveryContext context = recoveryContexts.computeIfAbsent(agentId, 
                k -> new RecoveryContext(agentId));

        if (!context.canAttemptRecovery(maxRecoveryAttempts)) {
            log.warn("Agent {} 恢复尝试次数已达上限", agentId);
            return RecoveryResult.failure("Max recovery attempts reached");
        }

        return doRecovery(agent, context);
    }

    private RecoveryResult doRecovery(AbstractAgent agent, RecoveryContext context) {
        String agentId = agent.getId();
        int attempt = context.getAttemptCount() + 1;
        long delay = calculateDelay(context.getAttemptCount());

        log.info("开始恢复 Agent {} (尝试 {}/{}, 延迟 {}ms)", 
                agentId, attempt, maxRecoveryAttempts, delay);

        context.incrementAttemptCount();
        context.setLastAttemptTime(new Date());

        notifyListeners(agentId, RecoveryPhase.STARTED, attempt);

        try {
            Thread.sleep(delay);

            agent.initialize();

            if (agent.getStatus() == AgentStatus.READY || 
                agent.getStatus() == AgentStatus.INITIALIZED) {
                
                context.setSuccessfulRecovery(true);
                context.setLastSuccessTime(new Date());
                context.resetAttemptCount();
                
                log.info("Agent {} 恢复成功", agentId);
                notifyListeners(agentId, RecoveryPhase.SUCCEEDED, attempt);
                
                return RecoveryResult.success(attempt);
            } else {
                log.warn("Agent {} 恢复后状态异常: {}", agentId, agent.getStatus());
                notifyListeners(agentId, RecoveryPhase.FAILED, attempt);
                
                return RecoveryResult.failure("Agent status not recovered: " + agent.getStatus(), attempt);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RecoveryResult.failure("Recovery interrupted", attempt);
        } catch (Exception e) {
            log.error("Agent {} 恢复失败", agentId, e);
            context.setLastError(e.getMessage());
            notifyListeners(agentId, RecoveryPhase.FAILED, attempt);
            
            return RecoveryResult.failure(e.getMessage(), attempt);
        }
    }

    private long calculateDelay(int attemptCount) {
        return (long) (recoveryDelayMs * Math.pow(backoffMultiplier, attemptCount));
    }

    public void resetRecoveryContext(String agentId) {
        recoveryContexts.remove(agentId);
        log.info("重置 Agent {} 的恢复上下文", agentId);
    }

    public void resetAllRecoveryContexts() {
        recoveryContexts.clear();
        log.info("重置所有 Agent 的恢复上下文");
    }

    private AbstractAgent findAgent(String agentId) {
        if (agents == null) return null;
        
        return agents.stream()
                .filter(a -> a.getId().equals(agentId))
                .findFirst()
                .orElse(null);
    }

    public RecoveryContext getRecoveryContext(String agentId) {
        return recoveryContexts.get(agentId);
    }

    public Map<String, RecoveryContext> getAllRecoveryContexts() {
        return new HashMap<>(recoveryContexts);
    }

    public void addListener(RecoveryListener listener) {
        listeners.add(listener);
    }

    public void removeListener(RecoveryListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(String agentId, RecoveryPhase phase, int attempt) {
        for (RecoveryListener listener : listeners) {
            try {
                listener.onRecoveryEvent(agentId, phase, attempt);
            } catch (Exception e) {
                log.error("恢复监听器执行失败", e);
            }
        }
    }

    public Map<String, Object> getRecoveryStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        
        int totalContexts = recoveryContexts.size();
        int activeRecoveries = 0;
        int successfulRecoveries = 0;
        int failedRecoveries = 0;

        for (RecoveryContext context : recoveryContexts.values()) {
            if (context.isCurrentlyRecovering()) {
                activeRecoveries++;
            }
            if (context.isSuccessfulRecovery()) {
                successfulRecoveries++;
            }
            if (context.getAttemptCount() >= maxRecoveryAttempts && !context.isSuccessfulRecovery()) {
                failedRecoveries++;
            }
        }

        stats.put("totalTrackedAgents", totalContexts);
        stats.put("activeRecoveries", activeRecoveries);
        stats.put("successfulRecoveries", successfulRecoveries);
        stats.put("failedRecoveries", failedRecoveries);
        stats.put("maxRecoveryAttempts", maxRecoveryAttempts);
        stats.put("recoveryDelayMs", recoveryDelayMs);
        stats.put("backoffMultiplier", backoffMultiplier);

        return stats;
    }

    public static class RecoveryContext {
        private final String agentId;
        private int attemptCount;
        private Date lastAttemptTime;
        private Date lastSuccessTime;
        private boolean successfulRecovery;
        private String lastError;

        public RecoveryContext(String agentId) {
            this.agentId = agentId;
            this.attemptCount = 0;
            this.successfulRecovery = false;
        }

        public boolean canAttemptRecovery(int maxAttempts) {
            return attemptCount < maxAttempts;
        }

        public void incrementAttemptCount() {
            this.attemptCount++;
        }

        public void resetAttemptCount() {
            this.attemptCount = 0;
        }

        public boolean isCurrentlyRecovering() {
            return attemptCount > 0 && !successfulRecovery;
        }

        public String getAgentId() { return agentId; }
        public int getAttemptCount() { return attemptCount; }
        public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
        public Date getLastAttemptTime() { return lastAttemptTime; }
        public void setLastAttemptTime(Date lastAttemptTime) { this.lastAttemptTime = lastAttemptTime; }
        public Date getLastSuccessTime() { return lastSuccessTime; }
        public void setLastSuccessTime(Date lastSuccessTime) { this.lastSuccessTime = lastSuccessTime; }
        public boolean isSuccessfulRecovery() { return successfulRecovery; }
        public void setSuccessfulRecovery(boolean successfulRecovery) { this.successfulRecovery = successfulRecovery; }
        public String getLastError() { return lastError; }
        public void setLastError(String lastError) { this.lastError = lastError; }
    }

    public static class RecoveryResult {
        private final boolean success;
        private final String message;
        private final int attempts;

        private RecoveryResult(boolean success, String message, int attempts) {
            this.success = success;
            this.message = message;
            this.attempts = attempts;
        }

        public static RecoveryResult success(int attempts) {
            return new RecoveryResult(true, "Recovery successful", attempts);
        }

        public static RecoveryResult failure(String message) {
            return new RecoveryResult(false, message, 0);
        }

        public static RecoveryResult failure(String message, int attempts) {
            return new RecoveryResult(false, message, attempts);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getAttempts() { return attempts; }
    }

    public enum RecoveryPhase {
        STARTED,
        SUCCEEDED,
        FAILED
    }

    @FunctionalInterface
    public interface RecoveryListener {
        void onRecoveryEvent(String agentId, RecoveryPhase phase, int attempt);
    }
}
