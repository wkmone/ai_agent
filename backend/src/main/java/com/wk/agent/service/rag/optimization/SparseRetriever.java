package com.wk.agent.service.rag.optimization;

import java.util.List;
import java.util.Map;

public interface SparseRetriever {
    List<SearchResult> search(String query, String ragNamespace, int topK);
    
    void addDocument(String id, String content, String ragNamespace, Map<String, Object> metadata);
    
    void deleteDocument(String id);
    
    void rebuildIndex(String ragNamespace);
}
