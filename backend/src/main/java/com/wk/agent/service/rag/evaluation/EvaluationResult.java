package com.wk.agent.service.rag.evaluation;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class EvaluationResult {
    private String evaluationId;
    private LocalDateTime timestamp;
    private Map<EvaluationMetric, Double> scores;
    private double overallScore;
    private String status;
    private String errorMessage;
    private Map<String, Object> metadata;
}
