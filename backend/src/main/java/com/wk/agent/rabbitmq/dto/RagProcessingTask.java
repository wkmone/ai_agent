package com.wk.agent.rabbitmq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagProcessingTask implements Serializable {
    private static final long serialVersionUID = 1L;

    private String taskId;
    private String ragNamespace;
    private Long knowledgeBaseId;
    private String sourcePath;
    private String fileContent;
    private String fileName;
    private String fileType;
    private int chunkSize;
    private int overlapSize;
    private String documentId;
    private long createdAt;
}
