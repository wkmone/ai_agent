package com.wk.agent.service.rag.advanced;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class DocumentHierarchy {
    private String parentDocumentId;
    private String parentContent;
    private String documentTitle;
    private List<ChildChunk> children;
    private int totalChunks;
}
