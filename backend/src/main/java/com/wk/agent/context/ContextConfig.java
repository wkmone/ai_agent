package com.wk.agent.context;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agent.context")
public class ContextConfig {

    private int maxTokens = 4000;
    
    private double reserveRatio = 0.2;
    
    private double minRelevance = 0.1;
    
    private boolean enableCompression = true;
    
    private double relevanceWeight = 0.7;
    
    private int maxHistoryMessages = 10;
    
    private int maxRagResults = 5;
    
    private int maxMemoryResults = 10;

    public void validate() {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        if (reserveRatio < 0 || reserveRatio > 1) {
            throw new IllegalArgumentException("reserveRatio must be between 0 and 1");
        }
        if (minRelevance < 0 || minRelevance > 1) {
            throw new IllegalArgumentException("minRelevance must be between 0 and 1");
        }
        if (relevanceWeight < 0 || relevanceWeight > 1) {
            throw new IllegalArgumentException("relevanceWeight must be between 0 and 1");
        }
    }

    public int getAvailableTokensForContent() {
        return (int) (maxTokens * (1 - reserveRatio));
    }

    public double getRecencyWeight() {
        return 1.0 - relevanceWeight;
    }
}
