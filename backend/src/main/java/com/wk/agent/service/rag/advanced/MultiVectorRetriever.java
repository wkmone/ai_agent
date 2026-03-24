package com.wk.agent.service.rag.advanced;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MultiVectorRetriever {
    private static final Logger log = LoggerFactory.getLogger(MultiVectorRetriever.class);
    
    @Autowired(required = false)
    private VectorStore vectorStore;
    
    @Value("${rag.advanced.multi-vector.enabled:false}")
    private boolean multiVectorEnabled;
    
    @Value("${rag.advanced.multi-vector.strategy:ensemble}")
    private String multiVectorStrategy;
    
    public enum MultiVectorStrategy {
        ENSEMBLE,
        MAX_SCORE,
        WEIGHTED_SUM
    }
    
    public List<Document> multiVectorSearch(List<String> queries, String ragNamespace, int topK, double threshold) {
        if (!multiVectorEnabled || vectorStore == null || queries == null || queries.isEmpty()) {
            return Collections.emptyList();
        }
        
        log.info("Multi-vector search with {} queries, strategy: {}", queries.size(), multiVectorStrategy);
        
        Map<String, Document> allDocs = new HashMap<>();
        Map<String, List<Double>> docScores = new HashMap<>();
        
        for (String query : queries) {
            List<Document> results = performSingleSearch(query, ragNamespace, topK * 2, threshold);
            
            for (int i = 0; i < results.size(); i++) {
                Document doc = results.get(i);
                String docId = doc.getId();
                
                allDocs.put(docId, doc);
                
                double score = calculateScore(doc, i);
                docScores.computeIfAbsent(docId, k -> new ArrayList<>()).add(score);
            }
        }
        
        List<Document> finalResults = fuseResults(allDocs, docScores, topK);
        
        log.info("Multi-vector search returned {} results", finalResults.size());
        return finalResults;
    }
    
    private List<Document> performSingleSearch(String query, String ragNamespace, int topK, double threshold) {
        try {
            SearchRequest.Builder searchBuilder = SearchRequest.builder()
                .query(query)
                .topK(topK);
            
            if (ragNamespace != null && !ragNamespace.isEmpty()) {
                searchBuilder.filterExpression("ragNamespace == '" + ragNamespace + "'");
            }
            
            return vectorStore.similaritySearch(searchBuilder.build());
        } catch (Exception e) {
            log.warn("Single vector search failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    private double calculateScore(Document doc, int rank) {
        Map<String, Object> metadata = doc.getMetadata();
        if (metadata != null && metadata.get("distance") != null) {
            Object distObj = metadata.get("distance");
            double distance = distObj instanceof Float ? ((Float) distObj).doubleValue() : (Double) distObj;
            return 1.0 - distance;
        }
        return 1.0 / (rank + 1);
    }
    
    private List<Document> fuseResults(Map<String, Document> allDocs, Map<String, List<Double>> docScores, int topK) {
        MultiVectorStrategy strategy = parseStrategy(multiVectorStrategy);
        
        Map<String, Double> finalScores = new HashMap<>();
        
        for (Map.Entry<String, List<Double>> entry : docScores.entrySet()) {
            String docId = entry.getKey();
            List<Double> scores = entry.getValue();
            
            double finalScore = switch (strategy) {
                case ENSEMBLE -> scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                case MAX_SCORE -> scores.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
                case WEIGHTED_SUM -> calculateWeightedSum(scores);
            };
            
            finalScores.put(docId, finalScore);
        }
        
        return finalScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(e -> allDocs.get(e.getKey()))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    private double calculateWeightedSum(List<Double> scores) {
        double sum = 0.0;
        double totalWeight = 0.0;
        
        for (int i = 0; i < scores.size(); i++) {
            double weight = 1.0 / (i + 1);
            sum += scores.get(i) * weight;
            totalWeight += weight;
        }
        
        return totalWeight > 0 ? sum / totalWeight : 0.0;
    }
    
    private MultiVectorStrategy parseStrategy(String strategy) {
        try {
            return MultiVectorStrategy.valueOf(strategy.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown multi-vector strategy: {}, using ENSEMBLE", strategy);
            return MultiVectorStrategy.ENSEMBLE;
        }
    }
    
    public boolean isEnabled() {
        return multiVectorEnabled;
    }
}
