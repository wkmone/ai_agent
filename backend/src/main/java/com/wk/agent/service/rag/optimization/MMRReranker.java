package com.wk.agent.service.rag.optimization;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MMRReranker {
    
    @Autowired(required = false)
    private EmbeddingModel embeddingModel;
    
    @Value("${rag.post-processing.mmr.lambda:0.7}")
    private double lambda;
    
    public List<SearchResult> rerank(List<SearchResult> results, String query, int topK) {
        if (embeddingModel == null || results.size() <= 1) {
            return results.size() > topK ? results.subList(0, topK) : results;
        }
        
        List<SearchResult> selected = new ArrayList<>();
        List<SearchResult> remaining = new ArrayList<>(results);
        
        Map<String, float[]> embeddings = new HashMap<>();
        float[] queryEmbedding = embeddingModel.embed(query);
        
        for (SearchResult result : results) {
            embeddings.put(result.getId(), embeddingModel.embed(result.getContent()));
        }
        
        while (selected.size() < topK && !remaining.isEmpty()) {
            SearchResult best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            
            for (SearchResult candidate : remaining) {
                double querySimilarity = cosineSimilarity(
                    queryEmbedding,
                    embeddings.get(candidate.getId())
                );
                
                double maxSimilarityToSelected = selected.stream()
                    .mapToDouble(s -> cosineSimilarity(
                        embeddings.get(candidate.getId()),
                        embeddings.get(s.getId())
                    ))
                    .max()
                    .orElse(0.0);
                
                double mmrScore = lambda * querySimilarity - (1 - lambda) * maxSimilarityToSelected;
                
                if (mmrScore > bestScore) {
                    bestScore = mmrScore;
                    best = candidate;
                }
            }
            
            if (best != null) {
                selected.add(best);
                remaining.remove(best);
            }
        }
        
        return selected;
    }
    
    private double cosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < a.length && i < b.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
