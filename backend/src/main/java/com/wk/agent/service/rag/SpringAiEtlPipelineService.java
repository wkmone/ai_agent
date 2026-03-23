package com.wk.agent.service.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SpringAiEtlPipelineService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiEtlPipelineService.class);

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_CHUNK_OVERLAP = 50;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    public List<Document> createDocumentsFromContent(String content, 
                                                       Map<String, Object> metadata) {
        return createDocumentsFromContent(content, metadata, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
    }

    public List<Document> createDocumentsFromContent(String content, 
                                                       Map<String, Object> metadata,
                                                       int chunkSize,
                                                       int chunkOverlap) {
        List<Document> documents = new ArrayList<>();
        
        if (content == null || content.trim().isEmpty()) {
            return documents;
        }

        List<String> chunks = splitTextIntoChunks(content, chunkSize, chunkOverlap);
        
        int chunkIndex = 0;
        for (String chunk : chunks) {
            Map<String, Object> chunkMetadata = new HashMap<>(metadata != null ? metadata : new HashMap<>());
            chunkMetadata.put("chunk_index", chunkIndex);
            chunkMetadata.put("total_chunks", chunks.size());
            chunkMetadata.put("source", metadata != null && metadata.containsKey("source") ? metadata.get("source") : "unknown");
            chunkMetadata.put("timestamp", System.currentTimeMillis());
            
            String docId = UUID.randomUUID().toString();
            documents.add(new Document(docId, chunk, chunkMetadata));
            chunkIndex++;
        }
        
        log.debug("将文本拆分为 {} 个文档块", documents.size());
        return documents;
    }

    private List<String> splitTextIntoChunks(String text, int chunkSize, int chunkOverlap) {
        List<String> chunks = new ArrayList<>();
        
        if (text.length() <= chunkSize) {
            chunks.add(text);
            return chunks;
        }

        String[] sentences = text.split("(?<=[。！？.!?])\\s*");
        StringBuilder currentChunk = new StringBuilder();
        
        for (String sentence : sentences) {
            if (currentChunk.length() + sentence.length() > chunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                
                if (chunkOverlap > 0 && currentChunk.length() > chunkOverlap) {
                    String overlapText = currentChunk.substring(currentChunk.length() - chunkOverlap);
                    currentChunk = new StringBuilder(overlapText);
                } else {
                    currentChunk = new StringBuilder();
                }
            }
            currentChunk.append(sentence);
        }
        
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        
        return chunks;
    }

    public void addDocumentsToVectorStore(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            log.warn("没有文档需要添加到向量存储");
            return;
        }
        log.info("添加 {} 个文档到向量存储", documents.size());
        try {
            vectorStore.add(documents);
            log.info("成功添加 {} 个文档到向量存储", documents.size());
        } catch (Exception e) {
            log.error("添加文档到向量存储失败: {}", e.getMessage(), e);
            throw new RuntimeException("添加文档到向量存储失败", e);
        }
    }

    public void processAndIndexContent(String content, Map<String, Object> metadata) {
        log.info("开始处理并索引内容");
        List<Document> documents = createDocumentsFromContent(content, metadata);
        addDocumentsToVectorStore(documents);
        log.info("内容处理和索引完成");
    }

    public List<Document> searchDocuments(String query, int topK, double similarityThreshold) {
        log.info("搜索文档: query={}, topK={}, threshold={}", query, topK, similarityThreshold);
        
        var searchRequest = org.springframework.ai.vectorstore.SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();
        
        List<Document> results = vectorStore.similaritySearch(searchRequest);
        log.info("找到 {} 个相关文档", results.size());
        return results;
    }

    public List<Document> searchDocuments(String query, int topK) {
        return searchDocuments(query, topK, 0.0);
    }

    public void deleteDocuments(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            log.warn("没有文档需要删除");
            return;
        }
        log.info("删除 {} 个文档", documentIds.size());
        try {
            vectorStore.delete(documentIds);
            log.info("成功删除 {} 个文档", documentIds.size());
        } catch (Exception e) {
            log.error("删除文档失败: {}", e.getMessage(), e);
        }
    }

    public float[] embedText(String text) {
        log.debug("为文本生成嵌入向量");
        return embeddingModel.embed(text);
    }

    public List<float[]> embedTexts(List<String> texts) {
        log.debug("为 {} 个文本生成嵌入向量", texts.size());
        return embeddingModel.embed(texts);
    }
}
