package com.wk.agent.core;

import com.wk.agent.service.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EnhancedToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(EnhancedToolExecutor.class);

    @Value("${agent.tool.enhanced.enabled:true}")
    private boolean enhancedEnabled;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private ToolRetryPolicy retryPolicy;

    @Autowired
    private FallbackToolService fallbackService;

    private final Map<String, ToolExecutionContext> activeContexts = new ConcurrentHashMap<>();
    private final List<ToolExecutionListener> listeners = new ArrayList<>();

    public static class ToolExecutionContext {
        private final String executionId;
        private final String toolName;
        private final Map<String, Object> params;
        private int attemptCount;
        private long startTime;
        private long totalDelayMs;
        private String lastError;
        private boolean success;
        private String result;

        public ToolExecutionContext(String executionId, String toolName, Map<String, Object> params) {
            this.executionId = executionId;
            this.toolName = toolName;
            this.params = params;
            this.attemptCount = 0;
            this.startTime = System.currentTimeMillis();
            this.totalDelayMs = 0;
            this.success = false;
        }

        public void incrementAttempt() { this.attemptCount++; }
        public void addDelay(long delayMs) { this.totalDelayMs += delayMs; }
        public void setLastError(String error) { this.lastError = error; }
        public void setSuccess(boolean success) { this.success = success; }
        public void setResult(String result) { this.result = result; }

        public String getExecutionId() { return executionId; }
        public String getToolName() { return toolName; }
        public Map<String, Object> getParams() { return params; }
        public int getAttemptCount() { return attemptCount; }
        public long getStartTime() { return startTime; }
        public long getTotalDelayMs() { return totalDelayMs; }
        public String getLastError() { return lastError; }
        public boolean isSuccess() { return success; }
        public String getResult() { return result; }
        public long getDuration() { return System.currentTimeMillis() - startTime; }
    }

    public String execute(String toolName, Map<String, Object> params) {
        if (!enhancedEnabled) {
            return toolRegistry.executeTool(toolName, params);
        }

        String executionId = UUID.randomUUID().toString().substring(0, 8);
        ToolExecutionContext context = new ToolExecutionContext(executionId, toolName, params);
        activeContexts.put(executionId, context);

        notifyListeners(context, ExecutionPhase.STARTED);

        try {
            String result = executeWithRetryAndFallback(context);
            context.setResult(result);
            context.setSuccess(!result.startsWith("❌") && !result.startsWith("错误:"));
            
            notifyListeners(context, ExecutionPhase.COMPLETED);
            return result;
            
        } catch (Exception e) {
            context.setLastError(e.getMessage());
            context.setSuccess(false);
            notifyListeners(context, ExecutionPhase.FAILED);
            return "❌ 工具执行失败: " + e.getMessage();
            
        } finally {
            activeContexts.remove(executionId);
        }
    }

    private String executeWithRetryAndFallback(ToolExecutionContext context) {
        String toolName = context.getToolName();
        Map<String, Object> params = context.getParams();
        String lastError = null;

        while (true) {
            context.incrementAttempt();
            int attempt = context.getAttemptCount();

            log.info("执行工具 {} (尝试 {}/{})", toolName, attempt, 
                    retryPolicy.getConfig(toolName).getMaxAttempts());

            notifyListeners(context, ExecutionPhase.ATTEMPT_START);

            try {
                String result = toolRegistry.executeTool(toolName, params);

                if (result == null) {
                    lastError = "工具返回空结果";
                } else if (result.startsWith("错误:")) {
                    lastError = result.substring(3).trim();
                } else {
                    retryPolicy.recordRetryAttempt(toolName, true, 0);
                    return result;
                }

            } catch (Exception e) {
                lastError = e.getMessage();
                log.warn("工具 {} 执行异常 (尝试 {}): {}", toolName, attempt, lastError);
            }

            if (!retryPolicy.shouldRetry(toolName, attempt, 
                    new RuntimeException(lastError))) {
                
                log.info("工具 {} 重试结束，尝试降级策略", toolName);
                retryPolicy.recordRetryAttempt(toolName, false, context.getTotalDelayMs());
                
                return fallbackService.executeWithFallback(toolName, params);
            }

            long delay = retryPolicy.calculateDelay(toolName, attempt);
            log.info("工具 {} 等待 {}ms 后重试", toolName, delay);
            
            context.addDelay(delay);
            notifyListeners(context, ExecutionPhase.RETRY_WAITING);

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "❌ 工具执行被中断";
            }
        }
    }

    public void addListener(ToolExecutionListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ToolExecutionListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(ToolExecutionContext context, ExecutionPhase phase) {
        for (ToolExecutionListener listener : listeners) {
            try {
                listener.onExecutionEvent(context, phase);
            } catch (Exception e) {
                log.error("工具执行监听器执行失败", e);
            }
        }
    }

    public Map<String, ToolExecutionContext> getActiveExecutions() {
        return new HashMap<>(activeContexts);
    }

    public int getActiveExecutionCount() {
        return activeContexts.size();
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        
        stats.put("enhancedEnabled", enhancedEnabled);
        stats.put("activeExecutions", activeContexts.size());
        stats.put("retryStats", retryPolicy.getAllStats());
        stats.put("fallbackStats", fallbackService.getAllStats());
        
        return stats;
    }

    public enum ExecutionPhase {
        STARTED,
        ATTEMPT_START,
        RETRY_WAITING,
        COMPLETED,
        FAILED
    }

    @FunctionalInterface
    public interface ToolExecutionListener {
        void onExecutionEvent(ToolExecutionContext context, ExecutionPhase phase);
    }
}
