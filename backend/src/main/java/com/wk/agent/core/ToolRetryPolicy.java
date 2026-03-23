package com.wk.agent.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ToolRetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(ToolRetryPolicy.class);

    @Value("${agent.tool.retry.max-attempts:3}")
    private int defaultMaxAttempts;

    @Value("${agent.tool.retry.initial-delay:1000}")
    private long defaultInitialDelay;

    @Value("${agent.tool.retry.multiplier:2.0}")
    private double defaultMultiplier;

    @Value("${agent.tool.retry.max-delay:30000}")
    private long defaultMaxDelay;

    private final Map<String, RetryConfig> toolConfigs = new ConcurrentHashMap<>();
    private final Map<String, RetryStats> retryStats = new ConcurrentHashMap<>();

    public static class RetryConfig {
        private int maxAttempts = 3;
        private long initialDelayMs = 1000;
        private double multiplier = 2.0;
        private long maxDelayMs = 30000;
        private Set<Class<? extends Exception>> retryableExceptions = new HashSet<>();
        private boolean retryOnAnyException = true;

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public long getInitialDelayMs() { return initialDelayMs; }
        public void setInitialDelayMs(long initialDelayMs) { this.initialDelayMs = initialDelayMs; }
        public double getMultiplier() { return multiplier; }
        public void setMultiplier(double multiplier) { this.multiplier = multiplier; }
        public long getMaxDelayMs() { return maxDelayMs; }
        public void setMaxDelayMs(long maxDelayMs) { this.maxDelayMs = maxDelayMs; }
        public Set<Class<? extends Exception>> getRetryableExceptions() { return retryableExceptions; }
        public boolean isRetryOnAnyException() { return retryOnAnyException; }
        public void setRetryOnAnyException(boolean retryOnAnyException) { this.retryOnAnyException = retryOnAnyException; }
    }

    public static class RetryStats {
        private int totalAttempts = 0;
        private int successfulRetries = 0;
        private int failedRetries = 0;
        private long totalRetryDelayMs = 0;

        public int getTotalAttempts() { return totalAttempts; }
        public void incrementTotalAttempts() { this.totalAttempts++; }
        public int getSuccessfulRetries() { return successfulRetries; }
        public void incrementSuccessfulRetries() { this.successfulRetries++; }
        public int getFailedRetries() { return failedRetries; }
        public void incrementFailedRetries() { this.failedRetries++; }
        public long getTotalRetryDelayMs() { return totalRetryDelayMs; }
        public void addRetryDelayMs(long delay) { this.totalRetryDelayMs += delay; }
    }

    public ToolRetryPolicy() {
        initializeDefaultConfigs();
    }

    private void initializeDefaultConfigs() {
        RetryConfig ragConfig = new RetryConfig();
        ragConfig.setMaxAttempts(3);
        ragConfig.setInitialDelayMs(2000);
        toolConfigs.put("rag_", ragConfig);

        RetryConfig terminalConfig = new RetryConfig();
        terminalConfig.setMaxAttempts(2);
        terminalConfig.setInitialDelayMs(500);
        toolConfigs.put("terminal_", terminalConfig);

        RetryConfig noteConfig = new RetryConfig();
        noteConfig.setMaxAttempts(2);
        noteConfig.setInitialDelayMs(1000);
        toolConfigs.put("note_", noteConfig);
    }

    public void configureTool(String toolName, RetryConfig config) {
        toolConfigs.put(toolName, config);
    }

    public RetryConfig getConfig(String toolName) {
        for (Map.Entry<String, RetryConfig> entry : toolConfigs.entrySet()) {
            if (toolName.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        RetryConfig defaultConfig = new RetryConfig();
        defaultConfig.setMaxAttempts(defaultMaxAttempts);
        defaultConfig.setInitialDelayMs(defaultInitialDelay);
        defaultConfig.setMultiplier(defaultMultiplier);
        defaultConfig.setMaxDelayMs(defaultMaxDelay);
        return defaultConfig;
    }

    public boolean shouldRetry(String toolName, int currentAttempt, Exception lastException) {
        RetryConfig config = getConfig(toolName);
        
        if (currentAttempt >= config.getMaxAttempts()) {
            log.debug("工具 {} 重试次数已达上限: {}", toolName, currentAttempt);
            return false;
        }

        if (!config.isRetryOnAnyException()) {
            boolean isRetryable = config.getRetryableExceptions().stream()
                    .anyMatch(exClass -> exClass.isInstance(lastException));
            if (!isRetryable) {
                log.debug("工具 {} 异常不可重试: {}", toolName, lastException.getClass().getSimpleName());
                return false;
            }
        }

        return true;
    }

    public long calculateDelay(String toolName, int attempt) {
        RetryConfig config = getConfig(toolName);
        long delay = (long) (config.getInitialDelayMs() * Math.pow(config.getMultiplier(), attempt - 1));
        return Math.min(delay, config.getMaxDelayMs());
    }

    public void recordRetryAttempt(String toolName, boolean success, long delayMs) {
        RetryStats stats = retryStats.computeIfAbsent(toolName, k -> new RetryStats());
        stats.incrementTotalAttempts();
        stats.addRetryDelayMs(delayMs);
        
        if (success) {
            stats.incrementSuccessfulRetries();
        } else {
            stats.incrementFailedRetries();
        }
    }

    public RetryStats getStats(String toolName) {
        return retryStats.get(toolName);
    }

    public Map<String, Object> getAllStats() {
        Map<String, Object> allStats = new LinkedHashMap<>();
        
        int totalTools = retryStats.size();
        int totalAttempts = 0;
        int successfulRetries = 0;
        int failedRetries = 0;
        long totalDelay = 0;

        for (Map.Entry<String, RetryStats> entry : retryStats.entrySet()) {
            RetryStats stats = entry.getValue();
            totalAttempts += stats.getTotalAttempts();
            successfulRetries += stats.getSuccessfulRetries();
            failedRetries += stats.getFailedRetries();
            totalDelay += stats.getTotalRetryDelayMs();
            
            allStats.put(entry.getKey(), Map.of(
                "totalAttempts", stats.getTotalAttempts(),
                "successfulRetries", stats.getSuccessfulRetries(),
                "failedRetries", stats.getFailedRetries(),
                "totalRetryDelayMs", stats.getTotalRetryDelayMs()
            ));
        }

        allStats.put("_summary", Map.of(
            "totalTools", totalTools,
            "totalAttempts", totalAttempts,
            "successfulRetries", successfulRetries,
            "failedRetries", failedRetries,
            "totalRetryDelayMs", totalDelay
        ));

        return allStats;
    }

    public void resetStats() {
        retryStats.clear();
        log.info("重置所有工具重试统计");
    }
}
