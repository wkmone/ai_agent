package com.wk.agent.service;

import com.wk.agent.entity.neo4j.ConceptNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class KnowledgeReasoningService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeReasoningService.class);

    @Autowired
    private KnowledgeGraphService knowledgeGraphService;

    public List<Map<String, Object>> inferTransitiveRelations(String conceptName, String relationType) {
        log.debug("推理传递关系: concept={}, relation={}", conceptName, relationType);
        
        List<Map<String, Object>> inferences = new ArrayList<>();
        List<ConceptNode> directRelations = knowledgeGraphService.getRelatedConcepts(conceptName);
        
        for (ConceptNode related : directRelations) {
            List<ConceptNode> secondLevel = knowledgeGraphService.getRelatedConcepts(related.getName());
            for (ConceptNode secondLevelConcept : secondLevel) {
                if (!secondLevelConcept.getName().equals(conceptName)) {
                    Map<String, Object> inference = new HashMap<>();
                    inference.put("source", conceptName);
                    inference.put("intermediate", related.getName());
                    inference.put("target", secondLevelConcept.getName());
                    inference.put("inferredRelation", relationType + "_transitive");
                    inference.put("confidence", 0.7);
                    inferences.add(inference);
                }
            }
        }
        
        return inferences;
    }

    public List<Map<String, Object>> findSimilarConcepts(String conceptName, double threshold) {
        log.debug("查找相似概念: concept={}, threshold={}", conceptName, threshold);
        
        List<Map<String, Object>> similarConcepts = new ArrayList<>();
        Optional<ConceptNode> sourceOpt = knowledgeGraphService.findConceptByName(conceptName);
        
        if (sourceOpt.isEmpty()) {
            return similarConcepts;
        }
        
        ConceptNode source = sourceOpt.get();
        List<ConceptNode> relatedConcepts = knowledgeGraphService.getRelatedConcepts(conceptName);
        Set<String> sourceRelatedNames = relatedConcepts.stream()
                .map(ConceptNode::getName)
                .collect(Collectors.toSet());
        
        for (ConceptNode related : relatedConcepts) {
            List<ConceptNode> secondLevelRelations = knowledgeGraphService.getRelatedConcepts(related.getName());
            for (ConceptNode candidate : secondLevelRelations) {
                if (!candidate.getName().equals(conceptName) && !sourceRelatedNames.contains(candidate.getName())) {
                    double similarity = calculateSimilarity(source, candidate);
                    if (similarity >= threshold) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("concept", candidate.getName());
                        result.put("similarity", similarity);
                        result.put("commonNeighbor", related.getName());
                        similarConcepts.add(result);
                    }
                }
            }
        }
        
        similarConcepts.sort((a, b) -> Double.compare((Double) b.get("similarity"), (Double) a.get("similarity")));
        return similarConcepts;
    }

    private double calculateSimilarity(ConceptNode a, ConceptNode b) {
        double similarity = 0.0;
        
        if (a.getCategory() != null && a.getCategory().equals(b.getCategory())) {
            similarity += 0.3;
        }
        
        List<ConceptNode> aRelations = knowledgeGraphService.getRelatedConcepts(a.getName());
        List<ConceptNode> bRelations = knowledgeGraphService.getRelatedConcepts(b.getName());
        
        Set<String> aRelatedNames = aRelations.stream().map(ConceptNode::getName).collect(Collectors.toSet());
        Set<String> bRelatedNames = bRelations.stream().map(ConceptNode::getName).collect(Collectors.toSet());
        
        Set<String> intersection = new HashSet<>(aRelatedNames);
        intersection.retainAll(bRelatedNames);
        
        Set<String> union = new HashSet<>(aRelatedNames);
        union.addAll(bRelatedNames);
        
        if (!union.isEmpty()) {
            similarity += 0.5 * ((double) intersection.size() / union.size());
        }
        
        if (a.getImportance() != null && b.getImportance() != null) {
            double importanceDiff = Math.abs(a.getImportance() - b.getImportance());
            similarity += 0.2 * (1 - importanceDiff);
        }
        
        return Math.min(similarity, 1.0);
    }

    public List<Map<String, Object>> inferHierarchy(String conceptName) {
        log.debug("推理概念层次: concept={}", conceptName);
        
        List<Map<String, Object>> hierarchy = new ArrayList<>();
        
        List<ConceptNode> relatedConcepts = knowledgeGraphService.getRelatedConcepts(conceptName);
        
        for (ConceptNode related : relatedConcepts) {
            Map<String, Object> level = new HashMap<>();
            level.put("concept", related.getName());
            level.put("category", related.getCategory());
            level.put("importance", related.getImportance());
            
            List<ConceptNode> subConcepts = knowledgeGraphService.getRelatedConcepts(related.getName());
            List<Map<String, Object>> subLevel = subConcepts.stream()
                    .filter(c -> !c.getName().equals(conceptName))
                    .map(c -> {
                        Map<String, Object> sub = new HashMap<>();
                        sub.put("concept", c.getName());
                        sub.put("category", c.getCategory());
                        sub.put("importance", c.getImportance());
                        return sub;
                    })
                    .collect(Collectors.toList());
            
            level.put("subConcepts", subLevel);
            hierarchy.add(level);
        }
        
        hierarchy.sort((a, b) -> {
            Double impA = (Double) a.getOrDefault("importance", 0.0);
            Double impB = (Double) b.getOrDefault("importance", 0.0);
            return Double.compare(impB, impA);
        });
        
        return hierarchy;
    }

    public List<Map<String, Object>> findKnowledgeGaps(String sessionId) {
        log.debug("发现知识缺口: session={}", sessionId);
        
        List<Map<String, Object>> gaps = new ArrayList<>();
        
        List<ConceptNode> concepts = knowledgeGraphService.getConceptsBySessionId(sessionId);
        
        for (ConceptNode concept : concepts) {
            List<ConceptNode> relations = knowledgeGraphService.getRelatedConcepts(concept.getName());
            
            if (relations.isEmpty() && concept.getImportance() != null && concept.getImportance() > 0.5) {
                Map<String, Object> gap = new HashMap<>();
                gap.put("type", "isolated_concept");
                gap.put("concept", concept.getName());
                gap.put("importance", concept.getImportance());
                gap.put("suggestion", "高重要性概念缺少关联，建议添加相关概念");
                gaps.add(gap);
            }
            
            if (concept.getDefinition() == null || concept.getDefinition().isEmpty()) {
                Map<String, Object> gap = new HashMap<>();
                gap.put("type", "missing_definition");
                gap.put("concept", concept.getName());
                gap.put("suggestion", "概念缺少定义，建议补充详细描述");
                gaps.add(gap);
            }
        }
        
        return gaps;
    }

    public Map<String, Object> reasonAboutQuery(String query, String sessionId) {
        log.debug("基于查询推理: query={}, session={}", query, sessionId);
        
        Map<String, Object> reasoning = new HashMap<>();
        
        String[] keywords = query.toLowerCase().split("\\s+");
        List<Map<String, Object>> relevantConcepts = new ArrayList<>();
        
        for (String keyword : keywords) {
            if (keyword.length() > 2) {
                List<ConceptNode> found = knowledgeGraphService.searchConcepts(keyword);
                for (ConceptNode concept : found) {
                    Map<String, Object> conceptInfo = new HashMap<>();
                    conceptInfo.put("name", concept.getName());
                    conceptInfo.put("category", concept.getCategory());
                    conceptInfo.put("definition", concept.getDefinition());
                    conceptInfo.put("importance", concept.getImportance());
                    
                    List<ConceptNode> related = knowledgeGraphService.getRelatedConcepts(concept.getName());
                    conceptInfo.put("relatedCount", related.size());
                    
                    relevantConcepts.add(conceptInfo);
                }
            }
        }
        
        relevantConcepts.sort((a, b) -> {
            Double impA = (Double) a.getOrDefault("importance", 0.0);
            Double impB = (Double) b.getOrDefault("importance", 0.0);
            return Double.compare(impB, impA);
        });
        
        reasoning.put("query", query);
        reasoning.put("relevantConcepts", relevantConcepts);
        reasoning.put("conceptCount", relevantConcepts.size());
        
        if (!relevantConcepts.isEmpty()) {
            List<Map<String, Object>> inferences = new ArrayList<>();
            for (Map<String, Object> concept : relevantConcepts) {
                String conceptName = (String) concept.get("name");
                List<Map<String, Object>> similar = findSimilarConcepts(conceptName, 0.5);
                if (!similar.isEmpty()) {
                    Map<String, Object> inference = new HashMap<>();
                    inference.put("fromConcept", conceptName);
                    inference.put("similarConcepts", similar.subList(0, Math.min(3, similar.size())));
                    inferences.add(inference);
                }
            }
            reasoning.put("inferences", inferences);
        }
        
        return reasoning;
    }

    public List<Map<String, Object>> recommendRelatedKnowledge(String conceptName, int limit) {
        log.debug("推荐相关知识: concept={}, limit={}", conceptName, limit);
        
        List<Map<String, Object>> recommendations = new ArrayList<>();
        
        List<Map<String, Object>> similar = findSimilarConcepts(conceptName, 0.3);
        
        for (int i = 0; i < Math.min(limit, similar.size()); i++) {
            Map<String, Object> sim = similar.get(i);
            Map<String, Object> rec = new HashMap<>();
            rec.put("type", "similar");
            rec.put("concept", sim.get("concept"));
            rec.put("reason", "基于结构相似性推荐");
            rec.put("score", sim.get("similarity"));
            recommendations.add(rec);
        }
        
        List<ConceptNode> related = knowledgeGraphService.getRelatedConcepts(conceptName);
        for (ConceptNode r : related) {
            if (recommendations.size() >= limit) break;
            
            Map<String, Object> rec = new HashMap<>();
            rec.put("type", "related");
            rec.put("concept", r.getName());
            rec.put("reason", "直接关联概念");
            rec.put("score", r.getImportance());
            recommendations.add(rec);
        }
        
        return recommendations;
    }
}
