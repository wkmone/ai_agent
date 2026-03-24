package com.wk.agent.service.rag.optimization;

import com.wk.agent.service.rag.evaluation.EvaluationRequest;
import com.wk.agent.service.rag.evaluation.EvaluationResult;
import com.wk.agent.service.rag.evaluation.MonitoringDashboard;
import com.wk.agent.service.rag.evaluation.RAGEvaluator;
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
public class HybridRetrievalService {
    private static final Logger log = LoggerFactory.getLogger(HybridRetrievalService.class);
    
    @Autowired(required = false)
    private VectorStore vectorStore;
    
    @Autowired(required = false)
    private SparseRetriever sparseRetriever;
    
    @Autowired(required = false)
    private ScoreFusion scoreFusion;
    
    @Autowired(required = false)
    private SemanticDeduplicator semanticDeduplicator;
    
    @Autowired(required = false)
    private MMRReranker mmrReranker;
    
    @Autowired(required = false)
    private QueryOptimizerService queryOptimizerService;
    
    @Autowired(required = false)
    private ContextExpander contextExpander;
    
    @Autowired(required = false)
    private RAGEvaluator ragEvaluator;
    
    @Autowired(required = false)
    private MonitoringDashboard monitoringDashboard;
    
    @Value("${rag.retrieval.hybrid.enabled:false}")
    private boolean hybridEnabled;
    
    @Value("${rag.post-processing.deduplication.enabled:false}")
    private boolean deduplicationEnabled;
    
    @Value("${rag.post-processing.mmr.enabled:false}")
    private boolean mmrEnabled;
    
    @Value("${rag.post-processing.context-expansion.enabled:false}")
    private boolean contextExpansionEnabled;
    
    @Value("${rag.evaluation.enabled:false}")
    private boolean evaluationEnabled;
    
    public List<Map<String, Object>> search(String query, String ragNamespace, int topK, double threshold) {
        return search(query, ragNamespace, topK, threshold, null);
    }
    
    public List<Map<String, Object>> search(String query, String ragNamespace, int topK, double threshold, List<String> context) {
        log.info("混合检索: query={}, namespace={}, topK={}, hybrid={}", query, ragNamespace, topK, hybridEnabled);
        
        long startTime = System.currentTimeMillis();
        
        String optimizedQuery = query;
        List<String> expandedQueries = null;
        
        if (queryOptimizerService != null) {
            QueryOptimizerService.OptimizedQuery optimized = queryOptimizerService.optimize(query, context);
            optimizedQuery = optimized.getRewrittenQuery();
            expandedQueries = optimized.getExpandedQueries();
        }
        
        List<SearchResult> results;
        
        if (hybridEnabled && sparseRetriever != null && scoreFusion != null) {
            results = performHybridSearch(optimizedQuery, ragNamespace, topK, threshold, expandedQueries);
        } else {
            results = performVectorSearch(optimizedQuery, ragNamespace, topK, threshold, expandedQueries);
        }
        
        if (deduplicationEnabled && semanticDeduplicator != null) {
            results = semanticDeduplicator.deduplicate(results);
        }
        
        if (mmrEnabled && mmrReranker != null) {
            results = mmrReranker.rerank(results, optimizedQuery, topK);
        }
        
        if (contextExpansionEnabled && contextExpander != null) {
            results = contextExpander.expand(results);
        }
        
        List<Map<String, Object>> finalResults = results.stream()
            .map(this::convertToMap)
            .collect(Collectors.toList());
        
        long duration = System.currentTimeMillis() - startTime;
        
        recordMetrics(query, ragNamespace, finalResults.size(), duration);
        
        if (evaluationEnabled && ragEvaluator != null && monitoringDashboard != null) {
            recordEvaluation(query, finalResults);
        }
        
        return finalResults;
    }
    
    private void recordMetrics(String query, String ragNamespace, int resultCount, long duration) {
        if (monitoringDashboard != null) {
            monitoringDashboard.incrementMetric("rag_search_total");
            monitoringDashboard.addMetric("rag_search_duration_ms", duration);
            monitoringDashboard.addMetric("rag_search_results_count", resultCount);
        }
    }
    
    private void recordEvaluation(String query, List<Map<String, Object>> results) {
        try {
            EvaluationRequest evalRequest = new EvaluationRequest();
            evalRequest.setQuestion(query);
            evalRequest.setContexts(results.stream()
                .map(r -> r.get("content") != null ? r.get("content").toString() : "")
                .collect(Collectors.toList()));
            evalRequest.setMetadata(Map.of("timestamp", System.currentTimeMillis()));
            
            EvaluationResult evalResult = ragEvaluator.evaluate(evalRequest);
            monitoringDashboard.recordEvaluation(evalResult);
            
            log.info("Evaluation completed - id: {}, overall score: {}", 
                evalResult.getEvaluationId(), evalResult.getOverallScore());
            
        } catch (Exception e) {
            log.warn("Failed to record evaluation: {}", e.getMessage());
        }
    }
    
    private List<SearchResult> performHybridSearch(String query, String ragNamespace, int topK, double threshold, List<String> expandedQueries) {
        List<String> queries = expandedQueries != null && !expandedQueries.isEmpty() 
            ? expandedQueries 
            : Collections.singletonList(query);
        
        int searchTopK = Math.max(topK * 3, 15);
        int perQueryTopK = Math.max(1, searchTopK / queries.size());
        
        Map<String, SearchResult> allResults = new LinkedHashMap<>();
        
        for (String q : queries) {
            List<SearchResult> vectorResults = performVectorSearch(q, ragNamespace, perQueryTopK, threshold, null);
            List<SearchResult> bm25Results = sparseRetriever.search(q, ragNamespace, perQueryTopK);
            
            List<SearchResult> fused = scoreFusion.fuse(vectorResults, bm25Results, perQueryTopK);
            
            for (SearchResult result : fused) {
                if (!allResults.containsKey(result.getId())) {
                    allResults.put(result.getId(), result);
                }
            }
        }
        
        return allResults.values().stream()
            .limit(topK)
            .collect(Collectors.toList());
    }
    
    private List<SearchResult> performVectorSearch(String query, String ragNamespace, int topK, double threshold) {
        return performVectorSearch(query, ragNamespace, topK, threshold, null);
    }
    
    private List<SearchResult> performVectorSearch(String query, String ragNamespace, int topK, double threshold, List<String> expandedQueries) {
        List<String> queries = expandedQueries != null && !expandedQueries.isEmpty() 
            ? expandedQueries 
            : Collections.singletonList(query);
        
        int perQueryTopK = Math.max(1, topK / queries.size());
        Map<String, SearchResult> allResults = new LinkedHashMap<>();
        
        if (vectorStore == null) {
            return new ArrayList<>();
        }
        
        try {
            for (String q : queries) {
                SearchRequest.Builder searchBuilder = SearchRequest.builder()
                    .query(q)
                    .topK(perQueryTopK);
                
                if (ragNamespace != null && !ragNamespace.isEmpty()) {
                    searchBuilder.filterExpression("ragNamespace == '" + ragNamespace + "'");
                }
                
                List<Document> docs = vectorStore.similaritySearch(searchBuilder.build());
                
                for (Document doc : docs) {
                    if (!allResults.containsKey(doc.getId())) {
                        Map<String, Object> metadata = doc.getMetadata();
                        Double distance = null;
                        if (metadata != null && metadata.get("distance") != null) {
                            Object distObj = metadata.get("distance");
                            if (distObj instanceof Float) {
                                distance = ((Float) distObj).doubleValue();
                            } else if (distObj instanceof Double) {
                                distance = (Double) distObj;
                            }
                        }
                        
                        double score = distance != null ? 1.0 - distance : 0.5;
                        
                        SearchResult result = SearchResult.builder()
                            .id(doc.getId())
                            .content(doc.getText())
                            .score(score)
                            .metadata(metadata)
                            .build();
                        
                        allResults.put(doc.getId(), result);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("向量检索失败: {}", e.getMessage());
        }
        
        return allResults.values().stream()
            .limit(topK)
            .collect(Collectors.toList());
    }
    
    private Map<String, Object> convertToMap(SearchResult result) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", result.getId());
        map.put("content", result.getContent());
        map.put("metadata", result.getMetadata());
        map.put("score", result.getScore());
        return map;
    }
    
    public void indexDocument(String id, String content, String ragNamespace, Map<String, Object> metadata) {
        if (sparseRetriever != null) {
            sparseRetriever.addDocument(id, content, ragNamespace, metadata);
        }
    }
    
    public void removeDocument(String id) {
        if (sparseRetriever != null) {
            sparseRetriever.deleteDocument(id);
        }
    }
}
