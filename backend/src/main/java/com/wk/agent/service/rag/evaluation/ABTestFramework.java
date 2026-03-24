package com.wk.agent.service.rag.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ABTestFramework {
    private static final Logger log = LoggerFactory.getLogger(ABTestFramework.class);
    
    private final Map<String, RetrievalConfig> configs = new ConcurrentHashMap<>();
    private final Map<String, String> userAssignments = new ConcurrentHashMap<>();
    private final Random random = new Random();
    
    @Value("${rag.evaluation.ab-test.enabled:false}")
    private boolean abTestEnabled;
    
    @Value("${rag.evaluation.ab-test.traffic-split:50}")
    private int trafficSplit;
    
    public ABTestFramework() {
        initDefaultConfigs();
    }
    
    private void initDefaultConfigs() {
        RetrievalConfig controlConfig = RetrievalConfig.builder()
            .configId("control")
            .configName("Control Group (Baseline)")
            .enableHybridRetrieval(false)
            .enableQueryRewrite(false)
            .enableIntentClassification(false)
            .enableSynonymExpansion(false)
            .enableDeduplication(false)
            .enableMMR(false)
            .enableContextExpansion(false)
            .build();
        
        RetrievalConfig treatmentConfig = RetrievalConfig.builder()
            .configId("treatment")
            .configName("Treatment Group (Optimized)")
            .enableHybridRetrieval(true)
            .enableQueryRewrite(true)
            .enableIntentClassification(true)
            .enableSynonymExpansion(true)
            .enableDeduplication(true)
            .enableMMR(true)
            .enableContextExpansion(true)
            .fusionMethod("rrf")
            .vectorWeight(0.6)
            .bm25Weight(0.4)
            .mmrLambda(0.7)
            .deduplicationThreshold(0.9)
            .contextWindowSize(2)
            .build();
        
        configs.put("control", controlConfig);
        configs.put("treatment", treatmentConfig);
    }
    
    public void registerConfig(String configId, RetrievalConfig config) {
        configs.put(configId, config);
        log.info("Registered A/B test config: {}", configId);
    }
    
    public RetrievalConfig getConfigForUser(String userId) {
        if (!abTestEnabled) {
            return configs.get("control");
        }
        
        String assignedConfig = userAssignments.computeIfAbsent(userId, id -> {
            int bucket = Math.abs(id.hashCode()) % 100;
            return bucket < trafficSplit ? "control" : "treatment";
        });
        
        RetrievalConfig config = configs.get(assignedConfig);
        log.debug("User {} assigned to config: {}", userId, config != null ? config.getConfigName() : "control");
        
        return config != null ? config : configs.get("control");
    }
    
    public String getGroupForUser(String userId) {
        if (!abTestEnabled) {
            return "control";
        }
        return userAssignments.computeIfAbsent(userId, id -> {
            int bucket = Math.abs(id.hashCode()) % 100;
            return bucket < trafficSplit ? "control" : "treatment";
        });
    }
    
    public void logEvent(String userId, String eventType, Map<String, Object> data) {
        if (!abTestEnabled) {
            return;
        }
        
        String group = getGroupForUser(userId);
        log.info("A/B Test Event - User: {}, Group: {}, Event: {}, Data: {}", 
            userId, group, eventType, data);
    }
    
    public Map<String, RetrievalConfig> getAllConfigs() {
        return Collections.unmodifiableMap(configs);
    }
    
    public void clearUserAssignments() {
        userAssignments.clear();
        log.info("Cleared all user A/B test assignments");
    }
    
    public boolean isAbTestEnabled() {
        return abTestEnabled;
    }
}
