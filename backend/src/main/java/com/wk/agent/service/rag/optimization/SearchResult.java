package com.wk.agent.service.rag.optimization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {
    private String id;
    private String content;
    private double score;
    private Map<String, Object> metadata;
    private boolean expanded;
    private int originalChunkIndex;
}
