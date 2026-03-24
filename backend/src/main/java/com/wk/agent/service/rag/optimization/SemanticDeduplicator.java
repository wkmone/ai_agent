package com.wk.agent.service.rag.optimization;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SemanticDeduplicator {
    
    @Autowired(required = false)
    private EmbeddingModel embeddingModel;
    
    @Value("${rag.post-processing.deduplication.similarity-threshold:0.9}")
    private double similarityThreshold;
    
    public List<SearchResult> deduplicate(List<SearchResult> results) {
        if (embeddingModel == null || results.size() <= 1) {
            return results;
        }
        
        List<SearchResult> deduplicated = new ArrayList<>();
        List<float[]> embeddings = new ArrayList<>();
        
        for (SearchResult result : results) {
            float[] embedding = embeddingModel.embed(result.getContent());
            
            boolean isDuplicate = false;
            for (float[] existingEmbedding : embeddings) {
                double similarity = cosineSimilarity(embedding, existingEmbedding);
                if (similarity > similarityThreshold) {
                    isDuplicate = true;
                    break;
                }
            }
            
            if (!isDuplicate) {
                deduplicated.add(result);
                embeddings.add(embedding);
            }
        }
        
        return deduplicated;
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
