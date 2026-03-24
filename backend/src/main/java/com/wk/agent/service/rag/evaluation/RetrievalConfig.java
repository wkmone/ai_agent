package com.wk.agent.service.rag.evaluation;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RetrievalConfig {
    private String configId;
    private String configName;
    
    private boolean enableHybridRetrieval;
    private boolean enableQueryRewrite;
    private boolean enableIntentClassification;
    private boolean enableSynonymExpansion;
    private boolean enableDeduplication;
    private boolean enableMMR;
    private boolean enableContextExpansion;
    
    private String fusionMethod;
    private double vectorWeight;
    private double bm25Weight;
    private double mmrLambda;
    private double deduplicationThreshold;
    private int contextWindowSize;
}
