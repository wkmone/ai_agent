package com.wk.agent.service.rag.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MonitoringDashboard {
    private static final Logger log = LoggerFactory.getLogger(MonitoringDashboard.class);
    
    private final Map<String, AtomicLong> metrics = new ConcurrentHashMap<>();
    private final List<EvaluationResult> recentEvaluations = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Map<String, Object>> abTestStats = new ConcurrentHashMap<>();
    
    private static final int MAX_RECENT_EVALUATIONS = 100;
    
    public void incrementMetric(String metricName) {
        metrics.computeIfAbsent(metricName, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    public void addMetric(String metricName, long value) {
        metrics.computeIfAbsent(metricName, k -> new AtomicLong(0)).addAndGet(value);
    }
    
    public void recordEvaluation(EvaluationResult result) {
        recentEvaluations.add(result);
        
        if (recentEvaluations.size() > MAX_RECENT_EVALUATIONS) {
            recentEvaluations.remove(0);
        }
    }
    
    public void recordABTestEvent(String userId, String group, String eventType, Map<String, Object> data) {
        String key = group + "_" + eventType;
        Map<String, Object> groupStats = abTestStats.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        
        AtomicLong count = (AtomicLong) groupStats.computeIfAbsent("count", k -> new AtomicLong(0));
        count.incrementAndGet();
        
        if (data != null && data.containsKey("duration")) {
            AtomicLong totalDuration = (AtomicLong) groupStats.computeIfAbsent("totalDuration", k -> new AtomicLong(0));
            totalDuration.addAndGet(((Number) data.get("duration")).longValue());
        }
    }
    
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        
        Map<String, Long> metricValues = new HashMap<>();
        for (Map.Entry<String, AtomicLong> entry : metrics.entrySet()) {
            metricValues.put(entry.getKey(), entry.getValue().get());
        }
        stats.put("metrics", metricValues);
        
        double avgScore = recentEvaluations.stream()
            .filter(r -> "SUCCESS".equals(r.getStatus()))
            .mapToDouble(EvaluationResult::getOverallScore)
            .average()
            .orElse(0.0);
        stats.put("averageEvaluationScore", avgScore);
        
        stats.put("totalEvaluations", recentEvaluations.size());
        
        Map<String, Map<String, Object>> abStats = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : abTestStats.entrySet()) {
            String[] parts = entry.getKey().split("_", 2);
            String group = parts[0];
            String event = parts[1];
            
            Map<String, Object> groupData = abStats.computeIfAbsent(group, k -> new HashMap<>());
            Map<String, Object> eventStats = new HashMap<>();
            
            AtomicLong count = (AtomicLong) entry.getValue().get("count");
            eventStats.put("count", count != null ? count.get() : 0);
            
            AtomicLong totalDuration = (AtomicLong) entry.getValue().get("totalDuration");
            if (totalDuration != null && count != null && count.get() > 0) {
                eventStats.put("avgDuration", totalDuration.get() / (double) count.get());
            }
            
            groupData.put(event, eventStats);
        }
        stats.put("abTestStats", abStats);
        
        stats.put("timestamp", LocalDateTime.now().toString());
        
        return stats;
    }
    
    public List<EvaluationResult> getRecentEvaluations(int limit) {
        int start = Math.max(0, recentEvaluations.size() - limit);
        return new ArrayList<>(recentEvaluations.subList(start, recentEvaluations.size()));
    }
    
    public void resetMetrics() {
        metrics.clear();
        recentEvaluations.clear();
        abTestStats.clear();
        log.info("Monitoring dashboard metrics reset");
    }
}
