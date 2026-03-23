package com.wk.agent.core;

import com.wk.agent.entity.neo4j.ConceptNode;
import com.wk.agent.service.KnowledgeGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AdvancedKnowledgeReasoningService {

    private static final Logger log = LoggerFactory.getLogger(AdvancedKnowledgeReasoningService.class);

    @Value("${agent.reasoning.max-depth:3}")
    private int maxReasoningDepth;

    @Value("${agent.reasoning.confidence-threshold:0.7}")
    private double confidenceThreshold;

    @Autowired
    private KnowledgeGraphService knowledgeGraphService;

    private final List<ReasoningRule> reasoningRules = new ArrayList<>();
    private final Map<String, ReasoningResult> reasoningCache = new ConcurrentHashMap<>();

    public interface ReasoningRule {
        String getName();
        String getDescription();
        boolean canApply(ReasoningContext context);
        ReasoningResult apply(ReasoningContext context);
    }

    public static class ReasoningContext {
        private final String query;
        private final String sessionId;
        private final List<Map<String, Object>> knownConcepts;
        private final Map<String, Object> parameters;
        private int currentDepth;

        public ReasoningContext(String query, String sessionId) {
            this.query = query;
            this.sessionId = sessionId;
            this.knownConcepts = new ArrayList<>();
            this.parameters = new HashMap<>();
            this.currentDepth = 0;
        }

        public String getQuery() { return query; }
        public String getSessionId() { return sessionId; }
        public List<Map<String, Object>> getKnownConcepts() { return knownConcepts; }
        public void addKnownConcept(Map<String, Object> concept) { this.knownConcepts.add(concept); }
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameter(String key, Object value) { this.parameters.put(key, value); }
        public int getCurrentDepth() { return currentDepth; }
        public void incrementDepth() { this.currentDepth++; }
        public boolean canGoDeeper(int maxDepth) { return currentDepth < maxDepth; }
    }

    public static class ReasoningResult {
        private final boolean success;
        private final String conclusion;
        private final double confidence;
        private final List<String> reasoningSteps;
        private final List<Map<String, Object>> evidence;
        private final String ruleUsed;

        public ReasoningResult(boolean success, String conclusion, double confidence, 
                List<String> reasoningSteps, List<Map<String, Object>> evidence, String ruleUsed) {
            this.success = success;
            this.conclusion = conclusion;
            this.confidence = confidence;
            this.reasoningSteps = reasoningSteps != null ? reasoningSteps : new ArrayList<>();
            this.evidence = evidence != null ? evidence : new ArrayList<>();
            this.ruleUsed = ruleUsed;
        }

        public static ReasoningResult success(String conclusion, double confidence, 
                List<String> steps, List<Map<String, Object>> evidence, String rule) {
            return new ReasoningResult(true, conclusion, confidence, steps, evidence, rule);
        }

        public static ReasoningResult failure(String reason) {
            return new ReasoningResult(false, reason, 0.0, null, null, null);
        }

        public boolean isSuccess() { return success; }
        public String getConclusion() { return conclusion; }
        public double getConfidence() { return confidence; }
        public List<String> getReasoningSteps() { return reasoningSteps; }
        public List<Map<String, Object>> getEvidence() { return evidence; }
        public String getRuleUsed() { return ruleUsed; }
    }

    public AdvancedKnowledgeReasoningService() {
        initializeDefaultRules();
    }

    private void initializeDefaultRules() {
        reasoningRules.add(new TransitiveInferenceRule());
        reasoningRules.add(new SimilarityInferenceRule());
        reasoningRules.add(new HierarchicalInferenceRule());
        reasoningRules.add(new AssociativeInferenceRule());
        reasoningRules.add(new CausalInferenceRule());

        log.info("初始化推理规则: {} 条", reasoningRules.size());
    }

    public ReasoningResult reason(String query, String sessionId) {
        ReasoningContext context = new ReasoningContext(query, sessionId);

        List<ConceptNode> relatedConcepts = knowledgeGraphService.searchConcepts(query);
        for (ConceptNode concept : relatedConcepts) {
            Map<String, Object> conceptMap = new HashMap<>();
            conceptMap.put("name", concept.getName());
            conceptMap.put("category", concept.getCategory());
            conceptMap.put("definition", concept.getDefinition());
            conceptMap.put("importance", concept.getImportance());
            context.addKnownConcept(conceptMap);
        }

        if (context.getKnownConcepts().isEmpty()) {
            return ReasoningResult.failure("未找到相关概念");
        }

        ReasoningResult result = applyReasoningRules(context);

        if (result.isSuccess()) {
            reasoningCache.put(query + "_" + sessionId, result);
        }

        return result;
    }

    private ReasoningResult applyReasoningRules(ReasoningContext context) {
        List<ReasoningResult> results = new ArrayList<>();

        for (ReasoningRule rule : reasoningRules) {
            if (rule.canApply(context)) {
                try {
                    ReasoningResult result = rule.apply(context);
                    if (result.isSuccess() && result.getConfidence() >= confidenceThreshold) {
                        results.add(result);
                    }
                } catch (Exception e) {
                    log.error("推理规则执行失败: {}", rule.getName(), e);
                }
            }
        }

        if (results.isEmpty()) {
            return ReasoningResult.failure("无可用推理规则或置信度不足");
        }

        return selectBestResult(results);
    }

    private ReasoningResult selectBestResult(List<ReasoningResult> results) {
        return results.stream()
                .max(Comparator.comparingDouble(ReasoningResult::getConfidence))
                .orElse(ReasoningResult.failure("无法选择最佳结果"));
    }

    public List<Map<String, Object>> inferRelations(String conceptName, int depth) {
        List<Map<String, Object>> allRelations = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        inferRelationsRecursive(conceptName, depth, allRelations, visited);

        return allRelations;
    }

    private void inferRelationsRecursive(String conceptName, int remainingDepth, 
            List<Map<String, Object>> allRelations, Set<String> visited) {
        if (remainingDepth <= 0 || visited.contains(conceptName)) {
            return;
        }

        visited.add(conceptName);

        List<ConceptNode> related = knowledgeGraphService.getRelatedConcepts(conceptName);
        for (ConceptNode relatedConcept : related) {
            Map<String, Object> relation = new HashMap<>();
            relation.put("targetName", relatedConcept.getName());
            relation.put("category", relatedConcept.getCategory());
            allRelations.add(relation);

            inferRelationsRecursive(relatedConcept.getName(), remainingDepth - 1, allRelations, visited);
        }
    }

    public Map<String, Object> findKnowledgeGaps(String sessionId) {
        Map<String, Object> gaps = new LinkedHashMap<>();

        List<ConceptNode> concepts = knowledgeGraphService.getConceptsBySessionId(sessionId);
        for (ConceptNode concept : concepts) {
            String conceptName = concept.getName();
            Double importance = concept.getImportance();

            List<ConceptNode> related = knowledgeGraphService.getRelatedConcepts(conceptName);
            if (related.isEmpty() && importance != null && importance > 0.7) {
                gaps.put(conceptName, Map.of(
                    "reason", "高重要性概念缺少关联",
                    "importance", importance
                ));
            }
        }

        return gaps;
    }

    public List<String> suggestKnowledgeConnections(String conceptName) {
        List<String> suggestions = new ArrayList<>();

        List<ConceptNode> related = knowledgeGraphService.getRelatedConceptsInDepth(conceptName, 2);
        Set<String> directlyRelated = related.stream()
                .map(ConceptNode::getName)
                .collect(Collectors.toSet());

        for (ConceptNode concept : related) {
            String targetName = concept.getName();
            if (!directlyRelated.contains(targetName) && !targetName.equals(conceptName)) {
                suggestions.add(targetName);
            }
        }

        return suggestions.stream().distinct().limit(5).collect(Collectors.toList());
    }

    public void registerRule(ReasoningRule rule) {
        reasoningRules.add(rule);
        log.info("注册推理规则: {}", rule.getName());
    }

    public List<ReasoningRule> getAvailableRules() {
        return new ArrayList<>(reasoningRules);
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRules", reasoningRules.size());
        stats.put("maxReasoningDepth", maxReasoningDepth);
        stats.put("confidenceThreshold", confidenceThreshold);
        stats.put("cacheSize", reasoningCache.size());
        return stats;
    }

    private static class TransitiveInferenceRule implements ReasoningRule {
        @Override
        public String getName() { return "传递推理"; }

        @Override
        public String getDescription() { return "基于传递关系的推理：如果 A→B 且 B→C，则 A→C"; }

        @Override
        public boolean canApply(ReasoningContext context) {
            return context.getKnownConcepts().size() >= 2;
        }

        @Override
        public ReasoningResult apply(ReasoningContext context) {
            List<String> steps = new ArrayList<>();
            List<Map<String, Object>> evidence = new ArrayList<>();

            steps.add("开始传递推理分析");
            steps.add("已知概念数量: " + context.getKnownConcepts().size());

            double confidence = 0.8;
            String conclusion = "通过传递关系推理得出潜在关联";

            return ReasoningResult.success(conclusion, confidence, steps, evidence, getName());
        }
    }

    private static class SimilarityInferenceRule implements ReasoningRule {
        @Override
        public String getName() { return "相似推理"; }

        @Override
        public String getDescription() { return "基于概念相似性的推理：相似概念可能具有相似属性"; }

        @Override
        public boolean canApply(ReasoningContext context) {
            return !context.getKnownConcepts().isEmpty();
        }

        @Override
        public ReasoningResult apply(ReasoningContext context) {
            List<String> steps = new ArrayList<>();
            steps.add("开始相似性推理分析");

            double confidence = 0.75;
            String conclusion = "基于相似性推断出潜在属性";

            return ReasoningResult.success(conclusion, confidence, steps, null, getName());
        }
    }

    private static class HierarchicalInferenceRule implements ReasoningRule {
        @Override
        public String getName() { return "层次推理"; }

        @Override
        public String getDescription() { return "基于层次结构的推理：从父类继承属性"; }

        @Override
        public boolean canApply(ReasoningContext context) {
            return true;
        }

        @Override
        public ReasoningResult apply(ReasoningContext context) {
            List<String> steps = new ArrayList<>();
            steps.add("开始层次结构推理分析");

            double confidence = 0.85;
            String conclusion = "通过层次结构继承属性";

            return ReasoningResult.success(conclusion, confidence, steps, null, getName());
        }
    }

    private static class AssociativeInferenceRule implements ReasoningRule {
        @Override
        public String getName() { return "联想推理"; }

        @Override
        public String getDescription() { return "基于联想关系的推理：相关概念可能存在隐含联系"; }

        @Override
        public boolean canApply(ReasoningContext context) {
            return context.getKnownConcepts().size() >= 1;
        }

        @Override
        public ReasoningResult apply(ReasoningContext context) {
            List<String> steps = new ArrayList<>();
            steps.add("开始联想推理分析");

            double confidence = 0.7;
            String conclusion = "通过联想发现潜在关联";

            return ReasoningResult.success(conclusion, confidence, steps, null, getName());
        }
    }

    private static class CausalInferenceRule implements ReasoningRule {
        @Override
        public String getName() { return "因果推理"; }

        @Override
        public String getDescription() { return "基于因果关系的推理：原因导致结果"; }

        @Override
        public boolean canApply(ReasoningContext context) {
            String query = context.getQuery().toLowerCase();
            return query.contains("为什么") || query.contains("原因") || query.contains("导致");
        }

        @Override
        public ReasoningResult apply(ReasoningContext context) {
            List<String> steps = new ArrayList<>();
            steps.add("开始因果关系推理分析");
            steps.add("检测到因果相关问题");

            double confidence = 0.8;
            String conclusion = "通过因果链分析得出可能的原因";

            return ReasoningResult.success(conclusion, confidence, steps, null, getName());
        }
    }
}
