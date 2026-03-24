package com.wk.agent.service.rag.advanced;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChildChunk {
    private String chunkId;
    private String content;
    private int startIndex;
    private int endIndex;
    private String headingPath;
    private int chunkIndex;
}
