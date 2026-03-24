package com.wk.agent.service.rag.optimization;

import java.util.List;
import java.util.Map;

public interface ChunkingStrategy {
    List<ChunkResult> chunk(String content, Map<String, Object> options);
    
    class ChunkResult {
        private final String content;
        private final int chunkIndex;
        private final String headingPath;
        private final int startOffset;
        private final int endOffset;
        private final int tokenCount;
        
        public ChunkResult(String content, int chunkIndex, String headingPath, 
                          int startOffset, int endOffset, int tokenCount) {
            this.content = content;
            this.chunkIndex = chunkIndex;
            this.headingPath = headingPath;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.tokenCount = tokenCount;
        }
        
        public String getContent() { return content; }
        public int getChunkIndex() { return chunkIndex; }
        public String getHeadingPath() { return headingPath; }
        public int getStartOffset() { return startOffset; }
        public int getEndOffset() { return endOffset; }
        public int getTokenCount() { return tokenCount; }
    }
}
