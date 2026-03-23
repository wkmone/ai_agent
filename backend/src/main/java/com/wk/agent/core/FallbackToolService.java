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
public class FallbackToolService {

    private static final Logger log = LoggerFactory.getLogger(FallbackToolService.class);

    @Value("${agent.tool.fallback.enabled:true}")
    private boolean fallbackEnabled;

    @Autowired
    private ToolRegistry toolRegistry;

    private final Map<String, List<FallbackStrategy>> fallbackStrategies = new ConcurrentHashMap<>();
    private final Map<String, FallbackStats> fallbackStats = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface FallbackStrategy {
        String execute(Map<String, Object> params, String originalError);
    }

    public static class FallbackStats {
        private int totalFallbacks = 0;
        private int successfulFallbacks = 0;
        private int failedFallbacks = 0;
        private String lastFallbackResult;

        public int getTotalFallbacks() { return totalFallbacks; }
        public void incrementTotalFallbacks() { this.totalFallbacks++; }
        public int getSuccessfulFallbacks() { return successfulFallbacks; }
        public void incrementSuccessfulFallbacks() { this.successfulFallbacks++; }
        public int getFailedFallbacks() { return failedFallbacks; }
        public void incrementFailedFallbacks() { this.failedFallbacks++; }
        public String getLastFallbackResult() { return lastFallbackResult; }
        public void setLastFallbackResult(String lastFallbackResult) { this.lastFallbackResult = lastFallbackResult; }
    }

    public FallbackToolService() {
        initializeDefaultFallbacks();
    }

    private void initializeDefaultFallbacks() {
        registerFallback("rag_search", (params, error) -> {
            log.info("RAG 搜索失败，降级到本地记忆搜索");
            return "⚠️ RAG 服务暂时不可用，请稍后重试。错误: " + error;
        });

        registerFallback("rag_ask", (params, error) -> {
            log.info("RAG 问答失败，降级到基础回答");
            String query = params.getOrDefault("query", "").toString();
            return "⚠️ 知识库服务暂时不可用，无法基于知识库回答问题: " + query;
        });

        registerFallback("rag_add_text", (params, error) -> {
            log.info("RAG 添加文本失败");
            return "⚠️ 知识库服务暂时不可用，文本未能添加。请稍后重试。";
        });

        registerFallback("terminal_run", (params, error) -> {
            log.info("终端命令执行失败");
            String command = params.getOrDefault("command", "").toString();
            return "⚠️ 终端命令执行失败: " + command + "。错误: " + error;
        });

        registerFallback("note_create", (params, error) -> {
            log.info("笔记创建失败");
            String title = params.getOrDefault("title", "").toString();
            return "⚠️ 笔记服务暂时不可用，未能创建笔记: " + title;
        });

        registerFallback("note_search", (params, error) -> {
            log.info("笔记搜索失败");
            return "⚠️ 笔记服务暂时不可用，无法搜索笔记。";
        });
    }

    public void registerFallback(String toolName, FallbackStrategy strategy) {
        fallbackStrategies.computeIfAbsent(toolName, k -> new ArrayList<>()).add(strategy);
    }

    public String executeWithFallback(String toolName, Map<String, Object> params) {
        if (!fallbackEnabled) {
            return toolRegistry.executeTool(toolName, params);
        }

        try {
            String result = toolRegistry.executeTool(toolName, params);
            
            if (result != null && result.startsWith("错误:")) {
                return attemptFallback(toolName, params, result);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("工具 {} 执行异常", toolName, e);
            return attemptFallback(toolName, params, e.getMessage());
        }
    }

    private String attemptFallback(String toolName, Map<String, Object> params, String error) {
        List<FallbackStrategy> strategies = fallbackStrategies.get(toolName);
        
        if (strategies == null || strategies.isEmpty()) {
            log.debug("工具 {} 没有注册降级策略", toolName);
            return "❌ 工具执行失败，无可用降级策略: " + error;
        }

        FallbackStats stats = fallbackStats.computeIfAbsent(toolName, k -> new FallbackStats());
        stats.incrementTotalFallbacks();

        for (int i = 0; i < strategies.size(); i++) {
            try {
                log.info("尝试工具 {} 的降级策略 #{}", toolName, i + 1);
                
                FallbackStrategy strategy = strategies.get(i);
                String result = strategy.execute(params, error);
                
                stats.incrementSuccessfulFallbacks();
                stats.setLastFallbackResult("成功 (策略 #" + (i + 1) + ")");
                
                log.info("工具 {} 降级策略 #{} 执行成功", toolName, i + 1);
                return result;
                
            } catch (Exception e) {
                log.warn("工具 {} 降级策略 #{} 执行失败: {}", toolName, i + 1, e.getMessage());
            }
        }

        stats.incrementFailedFallbacks();
        stats.setLastFallbackResult("所有降级策略失败");
        
        return "❌ 工具执行失败，所有降级策略均失败: " + error;
    }

    public boolean hasFallback(String toolName) {
        List<FallbackStrategy> strategies = fallbackStrategies.get(toolName);
        return strategies != null && !strategies.isEmpty();
    }

    public FallbackStats getStats(String toolName) {
        return fallbackStats.get(toolName);
    }

    public Map<String, Object> getAllStats() {
        Map<String, Object> allStats = new LinkedHashMap<>();
        
        int totalTools = fallbackStats.size();
        int totalFallbacks = 0;
        int successfulFallbacks = 0;
        int failedFallbacks = 0;

        for (Map.Entry<String, FallbackStats> entry : fallbackStats.entrySet()) {
            FallbackStats stats = entry.getValue();
            totalFallbacks += stats.getTotalFallbacks();
            successfulFallbacks += stats.getSuccessfulFallbacks();
            failedFallbacks += stats.getFailedFallbacks();
            
            allStats.put(entry.getKey(), Map.of(
                "totalFallbacks", stats.getTotalFallbacks(),
                "successfulFallbacks", stats.getSuccessfulFallbacks(),
                "failedFallbacks", stats.getFailedFallbacks(),
                "lastFallbackResult", stats.getLastFallbackResult() != null ? 
                    stats.getLastFallbackResult() : ""
            ));
        }

        allStats.put("_summary", Map.of(
            "totalToolsWithFallback", totalTools,
            "totalFallbacks", totalFallbacks,
            "successfulFallbacks", successfulFallbacks,
            "failedFallbacks", failedFallbacks,
            "fallbackEnabled", fallbackEnabled
        ));

        return allStats;
    }

    public void clearFallbacks(String toolName) {
        fallbackStrategies.remove(toolName);
        log.info("清除工具 {} 的所有降级策略", toolName);
    }

    public void clearAllFallbacks() {
        fallbackStrategies.clear();
        log.info("清除所有工具的降级策略");
    }

    public void resetStats() {
        fallbackStats.clear();
        log.info("重置所有降级统计");
    }
}
