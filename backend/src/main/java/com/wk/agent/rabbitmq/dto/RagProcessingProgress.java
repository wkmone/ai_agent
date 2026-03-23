package com.wk.agent.rabbitmq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagProcessingProgress implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status {
        PENDING,
        PARSING,
        CHUNKING,
        VECTORIZING,
        SAVING,
        COMPLETED,
        FAILED
    }

    private String taskId;
    private Status status;
    private String message;
    private double progress;
    private String documentId;
    private Integer chunkCount;
    private Integer totalTokens;
    private String errorMessage;
    private Map<String, Object> metadata;
    private long timestamp;
}
