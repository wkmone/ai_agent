package com.wk.agent.service.rag.optimization;

import java.util.List;

public interface ScoreFusion {
    List<SearchResult> fuse(List<SearchResult> vectorResults, List<SearchResult> bm25Results, int topK);
}
