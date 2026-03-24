package com.wk.agent.service.rag.advanced;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ParentChildIndexService {
    private static final Logger log = LoggerFactory.getLogger(ParentChildIndexService.class);
    
    @Value("${rag.advanced.parent-child-index.enabled:false}")
    private boolean parentChildIndexEnabled;
    
    private final Map<String, DocumentHierarchy> documentHierarchies = new ConcurrentHashMap<>();
    private final Map<String, String> chunkToParentMap = new ConcurrentHashMap<>();
    
    public void indexDocument(DocumentHierarchy hierarchy) {
        if (!parentChildIndexEnabled) {
            return;
        }
        
        documentHierarchies.put(hierarchy.getParentDocumentId(), hierarchy);
        
        if (hierarchy.getChildren() != null) {
            for (ChildChunk chunk : hierarchy.getChildren()) {
                chunkToParentMap.put(chunk.getChunkId(), hierarchy.getParentDocumentId());
            }
        }
        
        log.info("Indexed document hierarchy: {}, chunks: {}", 
            hierarchy.getParentDocumentId(), 
            hierarchy.getChildren() != null ? hierarchy.getChildren().size() : 0);
    }
    
    public DocumentHierarchy getDocumentHierarchy(String documentId) {
        return documentHierarchies.get(documentId);
    }
    
    public String getParentDocumentId(String chunkId) {
        return chunkToParentMap.get(chunkId);
    }
    
    public ChildChunk getChildChunk(String chunkId) {
        String parentId = chunkToParentMap.get(chunkId);
        if (parentId == null) {
            return null;
        }
        
        DocumentHierarchy hierarchy = documentHierarchies.get(parentId);
        if (hierarchy == null || hierarchy.getChildren() == null) {
            return null;
        }
        
        return hierarchy.getChildren().stream()
            .filter(c -> chunkId.equals(c.getChunkId()))
            .findFirst()
            .orElse(null);
    }
    
    public List<ChildChunk> getNeighborChunks(String chunkId, int windowSize) {
        String parentId = chunkToParentMap.get(chunkId);
        if (parentId == null) {
            return Collections.emptyList();
        }
        
        DocumentHierarchy hierarchy = documentHierarchies.get(parentId);
        if (hierarchy == null || hierarchy.getChildren() == null) {
            return Collections.emptyList();
        }
        
        ChildChunk targetChunk = getChildChunk(chunkId);
        if (targetChunk == null) {
            return Collections.emptyList();
        }
        
        List<ChildChunk> neighbors = new ArrayList<>();
        int targetIndex = targetChunk.getChunkIndex();
        
        for (ChildChunk chunk : hierarchy.getChildren()) {
            int chunkIndex = chunk.getChunkIndex();
            if (chunkIndex >= targetIndex - windowSize && chunkIndex <= targetIndex + windowSize) {
                neighbors.add(chunk);
            }
        }
        
        return neighbors;
    }
    
    public String getExpandedContent(String chunkId, int windowSize) {
        List<ChildChunk> neighbors = getNeighborChunks(chunkId, windowSize);
        if (neighbors.isEmpty()) {
            ChildChunk chunk = getChildChunk(chunkId);
            return chunk != null ? chunk.getContent() : "";
        }
        
        neighbors.sort(Comparator.comparingInt(ChildChunk::getChunkIndex));
        
        StringBuilder sb = new StringBuilder();
        for (ChildChunk chunk : neighbors) {
            if (chunkId.equals(chunk.getChunkId())) {
                sb.append("[核心内容] ");
            }
            sb.append(chunk.getContent());
            sb.append("\n\n");
        }
        
        return sb.toString();
    }
    
    public void removeDocument(String documentId) {
        DocumentHierarchy hierarchy = documentHierarchies.remove(documentId);
        if (hierarchy != null && hierarchy.getChildren() != null) {
            for (ChildChunk chunk : hierarchy.getChildren()) {
                chunkToParentMap.remove(chunk.getChunkId());
            }
        }
        log.info("Removed document hierarchy: {}", documentId);
    }
    
    public boolean isEnabled() {
        return parentChildIndexEnabled;
    }
    
    public void clear() {
        documentHierarchies.clear();
        chunkToParentMap.clear();
        log.info("Cleared parent-child index");
    }
}
