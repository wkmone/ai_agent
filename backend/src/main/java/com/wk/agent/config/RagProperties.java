package com.wk.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {
    
    private Query query = new Query();
    private Retrieval retrieval = new Retrieval();
    private PostProcessing postProcessing = new PostProcessing();
    
    @Data
    public static class Retrieval {
        private Hybrid hybrid = new Hybrid();
        private Bm25 bm25 = new Bm25();
    }
    
    @Data
    public static class Hybrid {
        private boolean enabled = false;
        private String fusionMethod = "rrf";
        private double vectorWeight = 0.6;
        private double bm25Weight = 0.4;
    }
    
    @Data
    public static class Bm25 {
        private String indexPath = "./data/bm25-index";
        private String analyzer = "standard";
    }
    
    @Data
    public static class PostProcessing {
        private Deduplication deduplication = new Deduplication();
        private Mmr mmr = new Mmr();
        private ContextExpansion contextExpansion = new ContextExpansion();
    }
    
    @Data
    public static class Deduplication {
        private boolean enabled = false;
        private double similarityThreshold = 0.9;
    }
    
    @Data
    public static class Mmr {
        private boolean enabled = false;
        private double lambda = 0.7;
    }
    
    @Data
    public static class ContextExpansion {
        private boolean enabled = false;
        private int windowSize = 2;
    }
    
    @Data
    public static class Query {
        private Rewrite rewrite = new Rewrite();
        private IntentClassification intentClassification = new IntentClassification();
        private SynonymExpansion synonymExpansion = new SynonymExpansion();
    }
    
    @Data
    public static class Rewrite {
        private boolean enabled = false;
        private boolean useLlm = true;
        private int maxHistory = 3;
    }
    
    @Data
    public static class IntentClassification {
        private boolean enabled = false;
    }
    
    @Data
    public static class SynonymExpansion {
        private boolean enabled = false;
        private String dictionaryPath;
    }
    
    @Data
    public static class Chunking {
        private String strategy = "fixed";
        private int maxChunkSize = 500;
        private int minChunkSize = 100;
        private int overlap = 50;
    }
    
    @Data
    public static class Evaluation {
        private boolean enabled = false;
        private List<String> metrics = new ArrayList<>();
        private AbTest abTest = new AbTest();
    }
    
    @Data
    public static class AbTest {
        private boolean enabled = false;
        private int trafficSplit = 50;
    }
    
    @Data
    public static class Advanced {
        private ParentChildIndex parentChildIndex = new ParentChildIndex();
        private MultiVector multiVector = new MultiVector();
        private DomainAdaptation domainAdaptation = new DomainAdaptation();
    }
    
    @Data
    public static class ParentChildIndex {
        private boolean enabled = false;
    }
    
    @Data
    public static class MultiVector {
        private boolean enabled = false;
        private String strategy = "ensemble";
    }
    
    @Data
    public static class DomainAdaptation {
        private boolean enabled = false;
        private String defaultDomain = "general";
    }
    
    private Chunking chunking = new Chunking();
    private Evaluation evaluation = new Evaluation();
    private Advanced advanced = new Advanced();
}
