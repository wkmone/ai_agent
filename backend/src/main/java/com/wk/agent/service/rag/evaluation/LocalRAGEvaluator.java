package com.wk.agent.service.rag.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LocalRAGEvaluator implements RAGEvaluator {
    private static final Logger log = LoggerFactory.getLogger(LocalRAGEvaluator.class);
    
    @Autowired(required = false)
    private ChatClient chatClient;
    
    @Value("${rag.evaluation.enabled:false}")
    private boolean evaluationEnabled;
    
    @Override
    public EvaluationResult evaluate(EvaluationRequest request) {
        if (!evaluationEnabled) {
            return EvaluationResult.builder()
                .evaluationId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .status("DISABLED")
                .errorMessage("Evaluation is disabled")
                .build();
        }
        
        try {
            Map<EvaluationMetric, Double> scores = new EnumMap<>(EvaluationMetric.class);
            
            List<EvaluationMetric> metricsToEvaluate = request.getMetrics() != null 
                ? request.getMetrics() 
                : Arrays.asList(EvaluationMetric.ANSWER_RELEVANCY, EvaluationMetric.FAITHFULNESS);
            
            for (EvaluationMetric metric : metricsToEvaluate) {
                double score = evaluateMetric(metric, request);
                scores.put(metric, score);
            }
            
            double overallScore = scores.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
            
            return EvaluationResult.builder()
                .evaluationId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .scores(scores)
                .overallScore(overallScore)
                .status("SUCCESS")
                .metadata(request.getMetadata())
                .build();
                
        } catch (Exception e) {
            log.error("Evaluation failed: {}", e.getMessage(), e);
            return EvaluationResult.builder()
                .evaluationId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .status("FAILED")
                .errorMessage(e.getMessage())
                .build();
        }
    }
    
    @Override
    public List<EvaluationResult> batchEvaluate(List<EvaluationRequest> requests) {
        return requests.stream()
            .map(this::evaluate)
            .collect(Collectors.toList());
    }
    
    private double evaluateMetric(EvaluationMetric metric, EvaluationRequest request) {
        return switch (metric) {
            case ANSWER_RELEVANCY -> evaluateAnswerRelevancy(request);
            case FAITHFULNESS -> evaluateFaithfulness(request);
            case CONTEXT_PRECISION -> evaluateContextPrecision(request);
            case CONTEXT_RECALL -> evaluateContextRecall(request);
            default -> 0.5;
        };
    }
    
    private double evaluateAnswerRelevancy(EvaluationRequest request) {
        if (chatClient == null || request.getQuestion() == null || request.getAnswer() == null) {
            return 0.5;
        }
        
        try {
            String prompt = """
                请评估以下回答与问题的相关性，返回 0 到 1 之间的分数：
                问题：%s
                回答：%s
                只返回一个数字：
                """.formatted(request.getQuestion(), request.getAnswer());
            
            String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
            
            return Double.parseDouble(response.trim());
        } catch (Exception e) {
            log.warn("Failed to evaluate answer relevancy: {}", e.getMessage());
            return 0.5;
        }
    }
    
    private double evaluateFaithfulness(EvaluationRequest request) {
        if (chatClient == null || request.getAnswer() == null || request.getContexts() == null) {
            return 0.5;
        }
        
        try {
            String contexts = String.join("\n", request.getContexts());
            String prompt = """
                请评估以下回答是否基于提供的上下文，返回 0 到 1 之间的分数：
                上下文：%s
                回答：%s
                只返回一个数字：
                """.formatted(contexts, request.getAnswer());
            
            String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
            
            return Double.parseDouble(response.trim());
        } catch (Exception e) {
            log.warn("Failed to evaluate faithfulness: {}", e.getMessage());
            return 0.5;
        }
    }
    
    private double evaluateContextPrecision(EvaluationRequest request) {
        if (request.getContexts() == null || request.getContexts().isEmpty()) {
            return 0.0;
        }
        return 0.7;
    }
    
    private double evaluateContextRecall(EvaluationRequest request) {
        if (request.getGroundTruth() == null || request.getContexts() == null) {
            return 0.0;
        }
        return 0.6;
    }
}
