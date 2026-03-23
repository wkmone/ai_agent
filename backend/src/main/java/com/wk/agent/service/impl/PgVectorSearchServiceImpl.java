package com.wk.agent.service.impl;

import com.wk.agent.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PgVectorSearchServiceImpl implements VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(PgVectorSearchServiceImpl.class);

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Override
    public double[] generateEmbedding(String text) {
        log.debug("生成文本嵌入向量: {}", text.substring(0, Math.min(50, text.length())));
        
        try {
            float[] embedding = embeddingModel.embed(text);
            double[] result = new double[embedding.length];
            for (int i = 0; i < embedding.length; i++) {
                result[i] = embedding[i];
            }
            return result;
        } catch (Exception e) {
            log.error("生成嵌入向量失败: {}", e.getMessage());
            return new double[1536];
        }
    }

    @Override
    public List<double[]> generateEmbeddings(List<String> texts) {
        List<double[]> embeddings = new ArrayList<>();
        for (String text : texts) {
            embeddings.add(generateEmbedding(text));
        }
        return embeddings;
    }

    @Override
    public List<Map<String, Object>> searchSimilar(double[] queryVector, String sessionId, int topK) {
        List<Map<String, Object>> results = new ArrayList<>();

        try {
            SearchRequest.Builder searchBuilder = SearchRequest.builder()
                    .topK(topK * 3);

            if (sessionId != null && !sessionId.isEmpty()) {
                searchBuilder.filterExpression("sessionId == '" + sessionId + "'");
            }

            List<Document> documents = vectorStore.similaritySearch(searchBuilder.build());

            for (Document doc : documents) {
                Map<String, Object> result = new HashMap<>();
                result.put("memoryId", doc.getId());
                result.put("content", doc.getText());
                result.put("score", doc.getMetadata().get("score"));
                result.put("memoryType", doc.getMetadata().get("memoryType"));
                result.put("metadata", doc.getMetadata());
                results.add(result);
            }
        } catch (Exception e) {
            log.error("向量搜索失败: {}", e.getMessage());
        }

        return results;
    }

    @Override
    public List<Map<String, Object>> searchSimilar(String query, String sessionId, int topK) {
        log.debug("执行向量搜索: query={}, sessionId={}, topK={}", query, sessionId, topK);

        try {
            SearchRequest.Builder searchBuilder = SearchRequest.builder()
                    .query(query)
                    .topK(topK * 3);

            if (sessionId != null && !sessionId.isEmpty()) {
                searchBuilder.filterExpression("sessionId == '" + sessionId + "'");
            }

            List<Document> documents = vectorStore.similaritySearch(searchBuilder.build());

            List<Map<String, Object>> results = new ArrayList<>();
            for (Document doc : documents) {
                Map<String, Object> result = new HashMap<>();
                result.put("memoryId", doc.getId());
                result.put("content", doc.getText());
                result.put("score", doc.getMetadata().get("score"));
                result.put("memoryType", doc.getMetadata().get("memoryType"));
                result.put("metadata", doc.getMetadata());
                results.add(result);
            }

            return results;
        } catch (Exception e) {
            log.error("向量搜索失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void indexMemory(String memoryId, String memoryType, String sessionId, String content, double[] embedding) {
        log.debug("索引记忆: id={}, type={}, sessionId={}", memoryId, memoryType, sessionId);

        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("memoryId", memoryId);
            metadata.put("memoryType", memoryType);
            metadata.put("sessionId", sessionId);
            metadata.put("timestamp", System.currentTimeMillis());

            Document document = new Document(memoryId, content, metadata);

            vectorStore.add(List.of(document));
        } catch (Exception e) {
            log.error("索引记忆失败: {}", e.getMessage());
        }
    }

    @Override
    public void deleteMemory(String memoryId) {
        log.debug("删除记忆索引: id={}", memoryId);

        try {
            vectorStore.delete(List.of(memoryId));
        } catch (Exception e) {
            log.error("删除记忆索引失败: {}", e.getMessage());
        }
    }

    @Override
    public void updateMemory(String memoryId, String sessionId, String content, double[] embedding) {
        log.debug("更新记忆索引: id={}, sessionId={}", memoryId, sessionId);

        try {
            vectorStore.delete(List.of(memoryId));

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("memoryId", memoryId);
            metadata.put("sessionId", sessionId);
            metadata.put("timestamp", System.currentTimeMillis());

            Document document = new Document(memoryId, content, metadata);
            vectorStore.add(List.of(document));
        } catch (Exception e) {
            log.error("更新记忆索引失败: {}", e.getMessage());
        }
    }

    @Override
    public double calculateSimilarity(double[] vector1, double[] vector2) {
        return calculateCosineSimilarity(vector1, vector2);
    }

    @Override
    public double calculateCosineSimilarity(double[] vector1, double[] vector2) {
        if (vector1 == null || vector2 == null || vector1.length != vector2.length) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            norm1 += vector1[i] * vector1[i];
            norm2 += vector2[i] * vector2[i];
        }
        
        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
