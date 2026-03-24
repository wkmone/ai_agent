package com.wk.agent.service;

import com.wk.agent.entity.RagChunk;
import com.wk.agent.mapper.RagChunkMapper;
import com.wk.agent.service.rag.DocumentChunkingService;
import com.wk.agent.service.rag.DocumentChunkingService.ChunkResult;
import com.wk.agent.service.rag.DocumentParserService;
import com.wk.agent.service.rag.RerankerService;
import com.wk.agent.service.rag.optimization.HybridRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final String CACHE_PREFIX = "rag:search:";
    private static final String DOC_VECTOR_PREFIX = "rag:doc:vectors:";
    private static final long CACHE_TTL_MINUTES = 30;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private ChatClient ragChatClient;

    @Autowired
    private DocumentChunkingService chunkingService;

    @Autowired
    private DocumentParserService documentParser;

    @Autowired
    private RerankerService rerankerService;

    @Autowired
    private RagChunkMapper ragChunkMapper;

    @Autowired
    private RagKnowledgeBaseService knowledgeBaseService;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired(required = false)
    private HybridRetrievalService hybridRetrievalService;

    public Map<String, Object> addMultipartFile(MultipartFile file, String ragNamespace,
                                                  int chunkSize, int overlapSize) {
        String originalFilename = file.getOriginalFilename();
        log.info("添加上传文件到RAG系统: {}", originalFilename);

        Map<String, Object> result = new HashMap<>();

        try {
            String documentId = UUID.randomUUID().toString();

            String content = documentParser.parseMultipartFile(file);
            if (content == null || content.isEmpty()) {
                return Map.of("success", false, "error", "无法读取文档内容");
            }

            List<ChunkResult> chunks = chunkingService.chunkText(content, chunkSize, overlapSize);

            List<Document> documents = new ArrayList<>();
            List<RagChunk> ragChunks = new ArrayList<>();
            List<String> vectorIds = new ArrayList<>();

            for (ChunkResult chunk : chunks) {
                String vectorId = UUID.randomUUID().toString();
                vectorIds.add(vectorId);
                
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("documentId", documentId);
                metadata.put("chunkIndex", chunk.chunkIndex);
                metadata.put("headingPath", chunk.headingPath != null ? chunk.headingPath : "");
                metadata.put("ragNamespace", ragNamespace);
                metadata.put("sourcePath", originalFilename != null ? originalFilename : "uploaded");

                Document doc = new Document(vectorId, chunk.content, metadata);
                documents.add(doc);

                RagChunk ragChunk = new RagChunk();
                ragChunk.setDocumentId(documentId);
                ragChunk.setContent(chunk.content);
                ragChunk.setChunkIndex(chunk.chunkIndex);
                ragChunk.setHeadingPath(chunk.headingPath != null ? chunk.headingPath : "");
                ragChunk.setStartOffset(chunk.startOffset);
                ragChunk.setEndOffset(chunk.endOffset);
                ragChunk.setTokenCount(chunk.tokenCount);
                ragChunk.setRagNamespace(ragNamespace);
                ragChunk.setSourcePath(originalFilename != null ? originalFilename : "uploaded");
                ragChunk.setCreatedAt(LocalDateTime.now());
                ragChunk.setUpdatedAt(LocalDateTime.now());
                ragChunks.add(ragChunk);
            }

            vectorStore.add(documents);
            saveVectorIds(documentId, vectorIds);
            
            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                if (hybridRetrievalService != null) {
                    hybridRetrievalService.indexDocument(
                        doc.getId(),
                        doc.getText(),
                        ragNamespace,
                        doc.getMetadata()
                    );
                }
            }

            for (RagChunk rc : ragChunks) {
                ragChunkMapper.insert(rc);
            }

            result.put("success", true);
            result.put("documentId", documentId);
            result.put("chunkCount", chunks.size());
            result.put("totalTokens", chunks.stream().mapToInt(c -> c.tokenCount).sum());

        } catch (Exception e) {
            log.error("添加上传文件失败: {}", e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    public Map<String, Object> addDocument(String filePath, String ragNamespace,
                                            int chunkSize, int overlapSize) {
        log.info("添加文档到RAG系统: {}", filePath);

        Map<String, Object> result = new HashMap<>();

        try {
            String documentId = UUID.randomUUID().toString();

            String content = documentParser.parseDocument(filePath);
            if (content == null || content.isEmpty()) {
                return Map.of("success", false, "error", "无法读取文档内容");
            }

            List<ChunkResult> chunks = chunkingService.chunkText(content, chunkSize, overlapSize);

            List<Document> documents = new ArrayList<>();
            List<RagChunk> ragChunks = new ArrayList<>();
            List<String> vectorIds = new ArrayList<>();

            for (ChunkResult chunk : chunks) {
                String vectorId = UUID.randomUUID().toString();
                vectorIds.add(vectorId);
                
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("documentId", documentId);
                metadata.put("chunkIndex", chunk.chunkIndex);
                metadata.put("headingPath", chunk.headingPath != null ? chunk.headingPath : "");
                metadata.put("ragNamespace", ragNamespace);
                metadata.put("sourcePath", filePath);

                Document doc = new Document(vectorId, chunk.content, metadata);
                documents.add(doc);

                RagChunk ragChunk = new RagChunk();
                ragChunk.setDocumentId(documentId);
                ragChunk.setContent(chunk.content);
                ragChunk.setChunkIndex(chunk.chunkIndex);
                ragChunk.setHeadingPath(chunk.headingPath != null ? chunk.headingPath : "");
                ragChunk.setStartOffset(chunk.startOffset);
                ragChunk.setEndOffset(chunk.endOffset);
                ragChunk.setTokenCount(chunk.tokenCount);
                ragChunk.setRagNamespace(ragNamespace);
                ragChunk.setSourcePath(filePath);
                ragChunk.setCreatedAt(LocalDateTime.now());
                ragChunk.setUpdatedAt(LocalDateTime.now());
                ragChunks.add(ragChunk);
            }

            vectorStore.add(documents);
            saveVectorIds(documentId, vectorIds);
            
            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                if (hybridRetrievalService != null) {
                    hybridRetrievalService.indexDocument(
                        doc.getId(),
                        doc.getText(),
                        ragNamespace,
                        doc.getMetadata()
                    );
                }
            }

            for (RagChunk rc : ragChunks) {
                ragChunkMapper.insert(rc);
            }

            result.put("success", true);
            result.put("documentId", documentId);
            result.put("chunkCount", chunks.size());
            result.put("totalTokens", chunks.stream().mapToInt(c -> c.tokenCount).sum());

        } catch (Exception e) {
            log.error("添加文档失败: {}", e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    public Map<String, Object> addText(String text, String ragNamespace,
                                        String documentId, int chunkSize, int overlapSize) {
        log.info("添加文本到RAG系统, namespace: {}", ragNamespace);

        Map<String, Object> result = new HashMap<>();

        try {
            if (documentId == null || documentId.isEmpty()) {
                documentId = UUID.randomUUID().toString();
            }

            List<ChunkResult> chunks = chunkingService.chunkText(text, chunkSize, overlapSize);

            List<Document> documents = new ArrayList<>();
            List<RagChunk> ragChunks = new ArrayList<>();
            List<String> vectorIds = new ArrayList<>();

            for (ChunkResult chunk : chunks) {
                String vectorId = UUID.randomUUID().toString();
                vectorIds.add(vectorId);
                
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("documentId", documentId);
                metadata.put("chunkIndex", chunk.chunkIndex);
                metadata.put("headingPath", chunk.headingPath != null ? chunk.headingPath : "");
                metadata.put("ragNamespace", ragNamespace);

                Document doc = new Document(vectorId, chunk.content, metadata);
                documents.add(doc);

                RagChunk ragChunk = new RagChunk();
                ragChunk.setDocumentId(documentId);
                ragChunk.setContent(chunk.content);
                ragChunk.setChunkIndex(chunk.chunkIndex);
                ragChunk.setHeadingPath(chunk.headingPath != null ? chunk.headingPath : "");
                ragChunk.setStartOffset(chunk.startOffset);
                ragChunk.setEndOffset(chunk.endOffset);
                ragChunk.setTokenCount(chunk.tokenCount);
                ragChunk.setRagNamespace(ragNamespace);
                ragChunk.setCreatedAt(LocalDateTime.now());
                ragChunk.setUpdatedAt(LocalDateTime.now());
                ragChunks.add(ragChunk);
            }

            vectorStore.add(documents);
            saveVectorIds(documentId, vectorIds);
            
            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                if (hybridRetrievalService != null) {
                    hybridRetrievalService.indexDocument(
                        doc.getId(),
                        doc.getText(),
                        ragNamespace,
                        doc.getMetadata()
                    );
                }
            }

            for (RagChunk rc : ragChunks) {
                ragChunkMapper.insert(rc);
            }

            result.put("success", true);
            result.put("documentId", documentId);
            result.put("chunkCount", chunks.size());

        } catch (Exception e) {
            log.error("添加文本失败: {}", e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    public List<Map<String, Object>> search(String query, String ragNamespace,
                                             int topK, double threshold) {
        return search(query, ragNamespace, topK, threshold, false);
    }

    public List<Map<String, Object>> search(String query, String ragNamespace,
                                             int topK, double threshold, boolean enableRerank) {
        log.info("RAG检索: query={}, namespace={}, threshold={}, rerank={}", query, ragNamespace, threshold, enableRerank);

        String cacheKey = buildCacheKey(query, ragNamespace, topK, threshold, enableRerank);
        
        List<Map<String, Object>> cachedResults = getFromCache(cacheKey);
        if (cachedResults != null) {
            log.debug("从缓存返回检索结果");
            return cachedResults;
        }

        List<Map<String, Object>> results = new ArrayList<>();

        try {
            if (hybridRetrievalService != null) {
                results = hybridRetrievalService.search(query, ragNamespace, topK, threshold);
            } else {
                results = performLegacySearch(query, ragNamespace, topK, threshold);
            }

            if (enableRerank && !results.isEmpty()) {
                results = rerankerService.rerank(query, results, topK);
            }

            saveToCache(cacheKey, results);

        } catch (Exception e) {
            log.error("检索失败: {}", e.getMessage());
        }

        return results;
    }
    
    private List<Map<String, Object>> performLegacySearch(String query, String ragNamespace,
                                                            int topK, double threshold) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        try {
            int searchTopK = Math.max(topK * 3, 15);
            
            SearchRequest.Builder searchBuilder = SearchRequest.builder()
                    .query(query)
                    .topK(searchTopK);

            if (ragNamespace != null && !ragNamespace.isEmpty()) {
                searchBuilder.filterExpression("ragNamespace == '" + ragNamespace + "'");
            }

            List<Document> docs = vectorStore.similaritySearch(searchBuilder.build());
            
            List<String> queryKeywords = extractKeywords(query);

            List<Map<String, Object>> scoredResults = new ArrayList<>();
            
            for (Document doc : docs) {
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
                
                String content = doc.getText();
                double keywordScore = calculateKeywordScore(content, queryKeywords);
                double combinedScore = calculateCombinedScore(distance, keywordScore);
                
                double distanceThreshold = 1.0 - threshold;
                if (distance != null && distance > distanceThreshold) {
                    combinedScore *= 0.5;
                }
                
                Map<String, Object> item = new HashMap<>();
                item.put("id", doc.getId());
                item.put("content", content);
                item.put("metadata", doc.getMetadata());
                item.put("distance", distance);
                item.put("keywordScore", keywordScore);
                item.put("combinedScore", combinedScore);
                scoredResults.add(item);
            }
            
            scoredResults.sort((a, b) -> {
                Double scoreA = (Double) a.get("combinedScore");
                Double scoreB = (Double) b.get("combinedScore");
                return Double.compare(scoreB, scoreA);
            });
            
            results = scoredResults.stream()
                    .limit(topK)
                    .collect(java.util.stream.Collectors.toList());
            
            for (Map<String, Object> result : results) {
                result.remove("keywordScore");
                result.remove("combinedScore");
            }
            
        } catch (Exception e) {
            log.error("传统检索失败: {}", e.getMessage());
        }
        
        return results;
    }
    
    private List<String> extractKeywords(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> keywords = new ArrayList<>();
        
        String[] words = text.split("[\\s,，。！？、；：\"''（）【】《》]+");
        for (String word : words) {
            if (word.length() >= 2) {
                keywords.add(word.toLowerCase());
            }
        }
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fa5') {
                keywords.add(String.valueOf(c));
            }
        }
        
        return keywords;
    }
    
    private double calculateKeywordScore(String content, List<String> queryKeywords) {
        if (content == null || queryKeywords.isEmpty()) {
            return 0.0;
        }
        
        String lowerContent = content.toLowerCase();
        int matchCount = 0;
        
        for (String keyword : queryKeywords) {
            if (lowerContent.contains(keyword)) {
                matchCount++;
            }
        }
        
        return (double) matchCount / queryKeywords.size();
    }
    
    private double calculateCombinedScore(Double distance, double keywordScore) {
        double vectorScore = 1.0 - (distance != null ? distance : 0.5);
        
        return vectorScore * 0.6 + keywordScore * 0.4;
    }

    public List<Map<String, Object>> searchWithRerank(String query, String ragNamespace,
                                                       int topK, double threshold,
                                                       String rerankType) {
        log.info("RAG检索(重排序): query={}, rerankType={}", query, rerankType);

        String cacheKey = buildCacheKey(query, ragNamespace, topK, threshold, rerankType);
        
        List<Map<String, Object>> cachedResults = getFromCache(cacheKey);
        if (cachedResults != null) {
            return cachedResults;
        }

        List<Map<String, Object>> results = new ArrayList<>();

        try {
            int searchTopK = topK * 3;
            
            SearchRequest.Builder searchBuilder = SearchRequest.builder()
                    .query(query)
                    .topK(searchTopK)
                    .similarityThreshold(threshold);

            if (ragNamespace != null && !ragNamespace.isEmpty()) {
                searchBuilder.filterExpression("ragNamespace == '" + ragNamespace + "'");
            }

            List<Document> docs = vectorStore.similaritySearch(searchBuilder.build());

            for (Document doc : docs) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", doc.getId());
                item.put("content", doc.getText());
                item.put("metadata", doc.getMetadata());
                results.add(item);
            }

            if (!results.isEmpty()) {
                if ("llm".equalsIgnoreCase(rerankType)) {
                    results = rerankerService.llmRerank(query, results, topK);
                } else {
                    results = rerankerService.rerank(query, results, topK);
                }
            }

            saveToCache(cacheKey, results);

        } catch (Exception e) {
            log.error("检索失败: {}", e.getMessage());
        }

        return results;
    }

    public String ask(String question, String ragNamespace, int topK, double threshold) {
        return ask(question, ragNamespace, topK, threshold, false);
    }

    public String ask(String question, String ragNamespace, int topK, double threshold, boolean enableRerank) {
        log.info("RAG问答: question={}, rerank={}", question, enableRerank);

        try {
            List<Map<String, Object>> searchResults = search(question, ragNamespace, topK, threshold, enableRerank);

            if (searchResults.isEmpty()) {
                return "抱歉，未找到相关信息来回答您的问题。";
            }

            StringBuilder context = new StringBuilder();
            context.append("你是一个知识问答助手。请根据以下检索到的信息，直接回答用户的问题。不要调用任何工具或函数，直接给出文字回答。\n\n");
            context.append("检索到的相关信息：\n\n");

            for (int i = 0; i < searchResults.size(); i++) {
                Map<String, Object> result = searchResults.get(i);
                context.append("【信息").append(i + 1).append("】\n");
                context.append(result.get("content")).append("\n\n");
            }

            context.append("用户问题：").append(question).append("\n\n");
            context.append("请直接回答：");

            String answer = ragChatClient.prompt()
                    .user(context.toString())
                    .call()
                    .content();

            return answer;

        } catch (Exception e) {
            log.error("问答失败: {}", e.getMessage());
            return "抱歉，无法回答您的问题。错误: " + e.getMessage();
        }
    }

    public List<String> expandQuery(String query, int expansions) {
        log.info("查询扩展: query={}, expansions={}", query, expansions);

        List<String> expandedQueries = new ArrayList<>();
        expandedQueries.add(query);

        try {
            String expansionPrompt = String.format(
                    "请为以下查询生成%d个语义等价但表述不同的查询，每行一个，不要编号：\n%s",
                    expansions, query
            );

            String response = chatClient.prompt()
                    .user(expansionPrompt)
                    .call()
                    .content();

            if (response != null && !response.isEmpty()) {
                String[] lines = response.split("\n");
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !expandedQueries.contains(trimmed)) {
                        expandedQueries.add(trimmed);
                    }
                }
            }

        } catch (Exception e) {
            log.warn("查询扩展失败: {}", e.getMessage());
        }

        return expandedQueries;
    }

    public String generateHypotheticalDocument(String query) {
        log.info("生成假设文档: query={}", query);

        try {
            String hydePrompt = String.format(
                    "请根据以下问题，生成一段可能的答案段落（不要分析过程，直接给出答案）：\n%s",
                    query
            );

            String hypotheticalDoc = chatClient.prompt()
                    .user(hydePrompt)
                    .call()
                    .content();

            return hypotheticalDoc;

        } catch (Exception e) {
            log.warn("生成假设文档失败: {}", e.getMessage());
            return null;
        }
    }

    public List<Map<String, Object>> advancedSearch(String query, String ragNamespace,
                                                     int topK, double threshold,
                                                     boolean enableMqe, int mqeExpansions,
                                                     boolean enableHyde) {
        return advancedSearch(query, ragNamespace, topK, threshold, enableMqe, mqeExpansions, enableHyde, false);
    }

    public List<Map<String, Object>> advancedSearch(String query, String ragNamespace,
                                                     int topK, double threshold,
                                                     boolean enableMqe, int mqeExpansions,
                                                     boolean enableHyde, boolean enableRerank) {
        log.info("高级检索: query={}, mqe={}, hyde={}, rerank={}", query, enableMqe, enableHyde, enableRerank);

        String cacheKey = buildCacheKey("advanced:" + query, ragNamespace, topK, threshold, 
            enableMqe + ":" + enableHyde + ":" + enableRerank);
        
        List<Map<String, Object>> cachedResults = getFromCache(cacheKey);
        if (cachedResults != null) {
            return cachedResults;
        }

        List<Map<String, Object>> allResults = new ArrayList<>();
        Map<String, Map<String, Object>> deduplicated = new HashMap<>();

        try {
            List<String> queries = new ArrayList<>();
            queries.add(query);

            if (enableMqe && mqeExpansions > 0) {
                queries.addAll(expandQuery(query, mqeExpansions));
            }

            if (enableHyde) {
                String hypotheticalDoc = generateHypotheticalDocument(query);
                if (hypotheticalDoc != null && !hypotheticalDoc.isEmpty()) {
                    queries.add(hypotheticalDoc);
                }
            }

            int poolSize = enableRerank ? topK * 4 : topK * 2;
            int perQuery = Math.max(1, poolSize / queries.size());

            for (String q : queries) {
                List<Map<String, Object>> results = search(q, ragNamespace, perQuery, threshold, false);
                for (Map<String, Object> result : results) {
                    String content = (String) result.get("content");
                    if (content != null) {
                        String key = content.substring(0, Math.min(100, content.length()));
                        if (!deduplicated.containsKey(key)) {
                            deduplicated.put(key, result);
                        }
                    }
                }
            }

            allResults = new ArrayList<>(deduplicated.values());

            if (enableRerank && !allResults.isEmpty()) {
                allResults = rerankerService.rerank(query, allResults, topK);
            } else if (allResults.size() > topK) {
                allResults = allResults.subList(0, topK);
            }

            saveToCache(cacheKey, allResults);

        } catch (Exception e) {
            log.error("高级检索失败: {}", e.getMessage());
        }

        return allResults;
    }

    public Map<String, Object> getStats(String ragNamespace) {
        Map<String, Object> stats = new HashMap<>();

        try {
            Integer count = ragChunkMapper.countByRagNamespace(ragNamespace);
            stats.put("chunkCount", count != null ? count : 0);
            stats.put("namespace", ragNamespace);
            stats.put("documentCount", getDocumentCount());
            
            List<RagChunk> chunks = ragChunkMapper.findByRagNamespace(ragNamespace);
            int totalTokens = chunks.stream()
                    .mapToInt(c -> c.getTokenCount() != null ? c.getTokenCount() : 0)
                    .sum();
            stats.put("totalTokens", totalTokens);

        } catch (Exception e) {
            log.error("获取统计信息失败: {}", e.getMessage());
        }

        return stats;
    }

    public List<Map<String, Object>> getDocuments(String ragNamespace) {
        log.info("获取文档列表: namespace={}", ragNamespace);

        List<Map<String, Object>> documents = new ArrayList<>();

        try {
            List<String> documentIds = ragChunkMapper.findDistinctDocumentIdsByNamespace(ragNamespace);

            for (String documentId : documentIds) {
                Map<String, Object> doc = new HashMap<>();
                doc.put("documentId", documentId);

                List<RagChunk> chunks = ragChunkMapper.findByDocumentId(documentId);
                doc.put("chunkCount", chunks.size());

                int totalTokens = chunks.stream()
                        .mapToInt(c -> c.getTokenCount() != null ? c.getTokenCount() : 0)
                        .sum();
                doc.put("totalTokens", totalTokens);

                if (!chunks.isEmpty()) {
                    RagChunk firstChunk = chunks.get(0);
                    doc.put("sourcePath", firstChunk.getSourcePath());
                    doc.put("sourceType", firstChunk.getSourceType());
                    doc.put("createdAt", firstChunk.getCreatedAt());
                    doc.put("headingPath", firstChunk.getHeadingPath());

                    String preview = firstChunk.getContent();
                    if (preview != null && preview.length() > 200) {
                        preview = preview.substring(0, 200) + "...";
                    }
                    doc.put("preview", preview);
                }

                documents.add(doc);
            }

        } catch (Exception e) {
            log.error("获取文档列表失败: {}", e.getMessage());
        }

        return documents;
    }

    public boolean deleteDocument(String documentId) {
        log.info("删除文档: {}", documentId);

        try {
            List<String> vectorIds = getVectorIds(documentId);
            if (vectorIds != null && !vectorIds.isEmpty()) {
                vectorStore.delete(vectorIds);
                
                if (hybridRetrievalService != null) {
                    for (String id : vectorIds) {
                        hybridRetrievalService.removeDocument(id);
                    }
                }
                
                deleteVectorIds(documentId);
            }
            
            ragChunkMapper.deleteByDocumentId(documentId);
            
            clearSearchCache();
            
            return true;
        } catch (Exception e) {
            log.error("删除文档失败: {}", e.getMessage());
            return false;
        }
    }

    private void saveVectorIds(String documentId, List<String> vectorIds) {
        if (redisTemplate != null) {
            String key = DOC_VECTOR_PREFIX + documentId;
            redisTemplate.opsForValue().set(key, vectorIds, 24, TimeUnit.HOURS);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getVectorIds(String documentId) {
        if (redisTemplate != null) {
            String key = DOC_VECTOR_PREFIX + documentId;
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof List) {
                return (List<String>) value;
            }
        }
        return new ArrayList<>();
    }

    private void deleteVectorIds(String documentId) {
        if (redisTemplate != null) {
            String key = DOC_VECTOR_PREFIX + documentId;
            redisTemplate.delete(key);
        }
    }

    private long getDocumentCount() {
        if (redisTemplate != null) {
            Set<String> keys = redisTemplate.keys(DOC_VECTOR_PREFIX + "*");
            return keys != null ? keys.size() : 0;
        }
        return 0;
    }

    private String buildCacheKey(String query, String namespace, int topK, double threshold, Object rerank) {
        return CACHE_PREFIX + query.hashCode() + ":" + namespace + ":" + topK + ":" + threshold + ":" + rerank;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getFromCache(String key) {
        if (redisTemplate != null) {
            try {
                Object value = redisTemplate.opsForValue().get(key);
                if (value instanceof List) {
                    return (List<Map<String, Object>>) value;
                }
            } catch (Exception e) {
                log.debug("缓存读取失败: {}", e.getMessage());
            }
        }
        return null;
    }

    private void saveToCache(String key, List<Map<String, Object>> results) {
        if (redisTemplate != null && results != null && !results.isEmpty()) {
            try {
                redisTemplate.opsForValue().set(key, results, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.debug("缓存写入失败: {}", e.getMessage());
            }
        }
    }

    private void clearSearchCache() {
        if (redisTemplate != null) {
            try {
                Set<String> keys = redisTemplate.keys(CACHE_PREFIX + "*");
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            } catch (Exception e) {
                log.debug("缓存清理失败: {}", e.getMessage());
            }
        }
    }

    public Map<String, Object> generateReport(String ragNamespace, String sessionId) {
        log.info("生成学习报告: namespace={}, session={}", ragNamespace, sessionId);

        Map<String, Object> report = new LinkedHashMap<>();

        try {
            List<RagChunk> chunks = ragChunkMapper.findByRagNamespace(ragNamespace);
            
            int totalTokens = chunks.stream()
                    .mapToInt(c -> c.getTokenCount() != null ? c.getTokenCount() : 0)
                    .sum();
            
            report.put("documentCount", getDocumentCount());
            report.put("chunkCount", chunks.size());
            report.put("totalTokens", totalTokens);
            report.put("avgChunkSize", chunks.isEmpty() ? 0 : totalTokens / chunks.size());
            
            Map<String, Long> contentDistribution = new HashMap<>();
            for (RagChunk chunk : chunks) {
                String source = chunk.getSourcePath();
                if (source != null && !source.isEmpty()) {
                    contentDistribution.merge(source, 1L, Long::sum);
                }
            }
            report.put("contentDistribution", contentDistribution);
            
            List<Map<String, Object>> topKeywords = new ArrayList<>();
            report.put("topKeywords", topKeywords);
            
            List<String> recommendations = new ArrayList<>();
            if (chunks.isEmpty()) {
                recommendations.add("知识库为空，建议添加更多文档");
            }
            if (totalTokens < 1000) {
                recommendations.add("知识库内容较少，建议添加更多相关文档以提升检索质量");
            }
            if (chunks.size() > 100 && totalTokens / chunks.size() < 100) {
                recommendations.add("平均分块较小，可能影响上下文完整性，建议增大分块大小");
            }
            report.put("recommendations", recommendations);
            
            report.put("generatedAt", LocalDateTime.now().toString());
            report.put("success", true);

        } catch (Exception e) {
            log.error("生成学习报告失败: {}", e.getMessage());
            report.put("success", false);
            report.put("error", e.getMessage());
        }

        return report;
    }

    public Map<String, Object> addMultipartFileWithKnowledgeBase(MultipartFile file, Long knowledgeBaseId,
                                                                   int chunkSize, int overlapSize) {
        if (!knowledgeBaseService.exists(knowledgeBaseId)) {
            return Map.of("success", false, "error", "知识库不存在");
        }

        var kb = knowledgeBaseService.getKnowledgeBaseById(knowledgeBaseId);
        var result = addMultipartFile(file, kb.getNamespace(), chunkSize, overlapSize);
        
        if (Boolean.TRUE.equals(result.get("success"))) {
            updateChunksWithKnowledgeBaseId(result.get("documentId").toString(), knowledgeBaseId);
        }
        
        return result;
    }

    public Map<String, Object> addDocumentWithKnowledgeBase(String filePath, Long knowledgeBaseId,
                                                             int chunkSize, int overlapSize) {
        if (!knowledgeBaseService.exists(knowledgeBaseId)) {
            return Map.of("success", false, "error", "知识库不存在");
        }

        var kb = knowledgeBaseService.getKnowledgeBaseById(knowledgeBaseId);
        var result = addDocument(filePath, kb.getNamespace(), chunkSize, overlapSize);
        
        if (Boolean.TRUE.equals(result.get("success"))) {
            updateChunksWithKnowledgeBaseId(result.get("documentId").toString(), knowledgeBaseId);
        }
        
        return result;
    }

    public Map<String, Object> addTextWithKnowledgeBase(String text, Long knowledgeBaseId,
                                                         String documentId, int chunkSize, int overlapSize) {
        if (!knowledgeBaseService.exists(knowledgeBaseId)) {
            return Map.of("success", false, "error", "知识库不存在");
        }

        var kb = knowledgeBaseService.getKnowledgeBaseById(knowledgeBaseId);
        var result = addText(text, kb.getNamespace(), documentId, chunkSize, overlapSize);
        
        if (Boolean.TRUE.equals(result.get("success"))) {
            updateChunksWithKnowledgeBaseId(result.get("documentId").toString(), knowledgeBaseId);
        }
        
        return result;
    }

    public List<Map<String, Object>> searchWithKnowledgeBase(String query, Long knowledgeBaseId,
                                                              int topK, double threshold, boolean enableRerank) {
        if (!knowledgeBaseService.exists(knowledgeBaseId)) {
            return new ArrayList<>();
        }

        var kb = knowledgeBaseService.getKnowledgeBaseById(knowledgeBaseId);
        return search(query, kb.getNamespace(), topK, threshold, enableRerank);
    }

    public String askWithKnowledgeBase(String question, Long knowledgeBaseId,
                                       int topK, double threshold, boolean enableRerank) {
        if (!knowledgeBaseService.exists(knowledgeBaseId)) {
            return "知识库不存在";
        }

        var kb = knowledgeBaseService.getKnowledgeBaseById(knowledgeBaseId);
        return ask(question, kb.getNamespace(), topK, threshold, enableRerank);
    }

    public List<Map<String, Object>> getDocumentsWithKnowledgeBase(Long knowledgeBaseId) {
        if (!knowledgeBaseService.exists(knowledgeBaseId)) {
            return new ArrayList<>();
        }

        log.info("获取文档列表: knowledgeBaseId={}", knowledgeBaseId);
        List<Map<String, Object>> documents = new ArrayList<>();

        try {
            List<String> documentIds = ragChunkMapper.findDistinctDocumentIdsByKnowledgeBaseId(knowledgeBaseId);

            for (String documentId : documentIds) {
                Map<String, Object> doc = new HashMap<>();
                doc.put("documentId", documentId);

                List<RagChunk> chunks = ragChunkMapper.findByDocumentId(documentId);
                doc.put("chunkCount", chunks.size());

                int totalTokens = chunks.stream()
                        .mapToInt(c -> c.getTokenCount() != null ? c.getTokenCount() : 0)
                        .sum();
                doc.put("totalTokens", totalTokens);

                if (!chunks.isEmpty()) {
                    RagChunk firstChunk = chunks.get(0);
                    doc.put("sourcePath", firstChunk.getSourcePath());
                    doc.put("sourceType", firstChunk.getSourceType());
                    doc.put("createdAt", firstChunk.getCreatedAt());
                    doc.put("headingPath", firstChunk.getHeadingPath());

                    String preview = firstChunk.getContent();
                    if (preview != null && preview.length() > 200) {
                        preview = preview.substring(0, 200) + "...";
                    }
                    doc.put("preview", preview);
                }

                documents.add(doc);
            }

        } catch (Exception e) {
            log.error("获取文档列表失败: {}", e.getMessage());
        }

        return documents;
    }

    private void updateChunksWithKnowledgeBaseId(String documentId, Long knowledgeBaseId) {
        List<RagChunk> chunks = ragChunkMapper.findByDocumentId(documentId);
        for (RagChunk chunk : chunks) {
            chunk.setKnowledgeBaseId(knowledgeBaseId);
            ragChunkMapper.updateById(chunk);
        }
    }
}
