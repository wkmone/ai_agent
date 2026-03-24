package com.wk.agent.service.rag.evaluation;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class EvaluationRequest {
    private String question;
    private String answer;
    private List<String> contexts;
    private String groundTruth;
    private List<EvaluationMetric> metrics;
    private Map<String, Object> metadata;
}
