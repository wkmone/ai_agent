package com.wk.agent.service.rag.optimization;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class RRFFusion implements ScoreFusion {
    private static final int K = 60;
    
    @Override
    public List<SearchResult> fuse(List<SearchResult> vectorResults, List<SearchResult> bm25Results, int topK) {
        Map<String, SearchResult> mergedResults = new HashMap<>();
        Map<String, Double> scores = new HashMap<>();
        
        for (int i = 0; i < vectorResults.size(); i++) {
            SearchResult result = vectorResults.get(i);
            mergedResults.put(result.getId(), result);
            scores.put(result.getId(), 1.0 / (K + i + 1));
        }
        
        for (int i = 0; i < bm25Results.size(); i++) {
            SearchResult result = bm25Results.get(i);
            mergedResults.put(result.getId(), result);
            scores.merge(result.getId(), 1.0 / (K + i + 1), Double::sum);
        }
        
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(entry -> {
                SearchResult result = mergedResults.get(entry.getKey());
                result.setScore(entry.getValue());
                return result;
            })
            .toList();
    }
}
