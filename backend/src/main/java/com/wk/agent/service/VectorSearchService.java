package com.wk.agent.service;

import java.util.List;
import java.util.Map;

public interface VectorSearchService {

    double[] generateEmbedding(String text);

    List<double[]> generateEmbeddings(List<String> texts);

    List<Map<String, Object>> searchSimilar(double[] queryVector, String sessionId, int topK);

    List<Map<String, Object>> searchSimilar(String query, String sessionId, int topK);

    void indexMemory(String memoryId, String memoryType, String sessionId, String content, double[] embedding);

    void deleteMemory(String memoryId);

    void updateMemory(String memoryId, String sessionId, String content, double[] embedding);

    double calculateSimilarity(double[] vector1, double[] vector2);

    double calculateCosineSimilarity(double[] vector1, double[] vector2);
}
