package com.wk.agent.service.rag.optimization;

import com.wk.agent.entity.RagChunk;
import com.wk.agent.mapper.RagChunkMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ContextExpander {
    private static final Logger log = LoggerFactory.getLogger(ContextExpander.class);
    
    @Autowired(required = false)
    private RagChunkMapper ragChunkMapper;
    
    @Value("${rag.post-processing.context-expansion.enabled:false}")
    private boolean enabled;
    
    @Value("${rag.post-processing.context-expansion.window-size:2}")
    private int windowSize;
    
    public List<SearchResult> expand(List<SearchResult> results) {
        if (!enabled || ragChunkMapper == null || results == null || results.isEmpty()) {
            return results;
        }
        
        List<SearchResult> expanded = results.stream()
            .map(this::expandSingleResult)
            .collect(Collectors.toList());
        
        log.info("上下文窗口扩展: 原始结果数={}, 扩展后结果数={}", results.size(), expanded.size());
        return expanded;
    }
    
    private SearchResult expandSingleResult(SearchResult result) {
        try {
            Map<String, Object> metadata = result.getMetadata();
            if (metadata == null) {
                return result;
            }
            
            Object documentIdObj = metadata.get("documentId");
            Object chunkIndexObj = metadata.get("chunkIndex");
            
            if (documentIdObj == null || chunkIndexObj == null) {
                return result;
            }
            
            String documentId = documentIdObj.toString();
            int chunkIndex;
            
            if (chunkIndexObj instanceof Integer) {
                chunkIndex = (Integer) chunkIndexObj;
            } else if (chunkIndexObj instanceof Long) {
                chunkIndex = ((Long) chunkIndexObj).intValue();
            } else {
                chunkIndex = Integer.parseInt(chunkIndexObj.toString());
            }
            
            int startIndex = chunkIndex - windowSize;
            int endIndex = chunkIndex + windowSize;
            
            List<RagChunk> neighbors = ragChunkMapper.findByDocumentIdAndIndexRange(documentId, startIndex, endIndex);
            
            if (neighbors.isEmpty()) {
                return result;
            }
            
            String fullContent = mergeChunks(neighbors, chunkIndex);
            
            SearchResult expandedResult = SearchResult.builder()
                .id(result.getId())
                .content(fullContent)
                .score(result.getScore())
                .metadata(result.getMetadata())
                .expanded(true)
                .originalChunkIndex(chunkIndex)
                .build();
            
            return expandedResult;
            
        } catch (Exception e) {
            log.warn("上下文扩展失败，使用原始结果: {}", e.getMessage());
            return result;
        }
    }
    
    private String mergeChunks(List<RagChunk> chunks, int targetIndex) {
        chunks.sort(Comparator.comparingInt(RagChunk::getChunkIndex));
        
        StringBuilder sb = new StringBuilder();
        for (RagChunk chunk : chunks) {
            if (chunk.getChunkIndex() == targetIndex) {
                sb.append("[核心内容] ");
            }
            sb.append(chunk.getContent());
            sb.append("\n\n");
        }
        
        return sb.toString();
    }
}
