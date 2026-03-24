package com.wk.agent.service.rag.evaluation;

import java.util.List;

public interface RAGEvaluator {
    EvaluationResult evaluate(EvaluationRequest request);
    
    List<EvaluationResult> batchEvaluate(List<EvaluationRequest> requests);
}
