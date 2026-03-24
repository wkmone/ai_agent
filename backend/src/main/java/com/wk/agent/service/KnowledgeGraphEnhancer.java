package com.wk.agent.service;

import com.wk.agent.entity.neo4j.ConceptNode;
import com.wk.agent.service.KnowledgeGraphService;
import com.wk.agent.service.KnowledgeReasoningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class KnowledgeGraphEnhancer {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphEnhancer.class);

    @Autowired
    private KnowledgeGraphService knowledgeGraphService;

    @Autowired
    private KnowledgeReasoningService knowledgeReasoningService;

    public String enhanceContext(String sessionId, String userQuery) {
        log.debug("增强上下文: sessionId={}, query={}", sessionId, userQuery);

        if (userQuery == null || userQuery.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();

        List<String> keywords = extractKeywords(userQuery);
        if (keywords.isEmpty()) {
            return "";
        }

        List<ConceptNode> relatedConcepts = findRelatedConcepts(keywords);
        if (!relatedConcepts.isEmpty()) {
            context.append("【知识图谱 - 相关概念】\n");
            int index = 1;
            for (ConceptNode concept : relatedConcepts) {
                context.append(index++).append(". ").append(concept.getName());
                if (concept.getDefinition() != null && !concept.getDefinition().isEmpty()) {
                    context.append(": ").append(concept.getDefinition());
                }
                context.append("\n");
                if (index > 5) break;
            }
            context.append("\n");
        }

        List<String> inferredKnowledge = performReasoning(keywords, sessionId);
        if (!inferredKnowledge.isEmpty()) {
            context.append("【知识图谱 - 推理知识】\n");
            int index = 1;
            for (String inference : inferredKnowledge) {
                context.append(index++).append(". ").append(inference).append("\n");
                if (index > 5) break;
            }
            context.append("\n");
        }

        List<ConceptNode> sessionConcepts = knowledgeGraphService.getConceptsBySessionId(sessionId);
        if (!sessionConcepts.isEmpty()) {
            context.append("【知识图谱 - 会话知识】\n");
            int index = 1;
            for (ConceptNode concept : sessionConcepts) {
                if (concept.getImportance() != null && concept.getImportance() >= 0.7) {
                    context.append(index++).append(". ").append(concept.getName());
                    if (concept.getDefinition() != null && !concept.getDefinition().isEmpty()) {
                        context.append(": ").append(concept.getDefinition());
                    }
                    context.append("\n");
                    if (index > 5) break;
                }
            }
            context.append("\n");
        }

        return context.toString();
    }

    private List<String> extractKeywords(String query) {
        List<String> keywords = new ArrayList<>();
        String[] words = query.split("[\\s,，。.!！?？;；:：\"\"''()（）【】\\[\\]]+");
        for (String word : words) {
            if (word.length() >= 2) {
                keywords.add(word);
            }
        }
        return keywords;
    }

    private List<ConceptNode> findRelatedConcepts(List<String> keywords) {
        Set<ConceptNode> concepts = new LinkedHashSet<>();
        
        for (String keyword : keywords) {
            Optional<ConceptNode> concept = knowledgeGraphService.findConceptByName(keyword);
            concept.ifPresent(concepts::add);
            
            List<ConceptNode> related = knowledgeGraphService.getRelatedConcepts(keyword);
            concepts.addAll(related);
        }
        
        return new ArrayList<>(concepts);
    }

    private List<String> performReasoning(List<String> keywords, String sessionId) {
        List<String> inferences = new ArrayList<>();

        for (String keyword : keywords) {
            Optional<ConceptNode> concept = knowledgeGraphService.findConceptByName(keyword);
            if (concept.isPresent()) {
                List<Map<String, Object>> transitive = knowledgeReasoningService.inferTransitiveRelations(keyword, "RELATED_TO");
                for (Map<String, Object> inference : transitive) {
                    inferences.add(String.format("%s 通过 %s 关联到 %s", 
                        inference.get("source"), 
                        inference.get("intermediate"), 
                        inference.get("target")));
                }

                List<Map<String, Object>> similar = knowledgeReasoningService.findSimilarConcepts(keyword, 0.5);
                if (!similar.isEmpty()) {
                    List<String> similarNames = similar.stream()
                        .map(m -> (String) m.get("concept"))
                        .limit(3)
                        .collect(Collectors.toList());
                    if (!similarNames.isEmpty()) {
                        inferences.add("与 \"" + keyword + "\" 相似的概念有: " + String.join(", ", similarNames));
                    }
                }

                List<Map<String, Object>> hierarchy = knowledgeReasoningService.inferHierarchy(keyword);
                for (Map<String, Object> level : hierarchy) {
                    inferences.add(String.format("%s 的相关概念: %s", 
                        keyword, 
                        level.get("concept")));
                }
            }
        }

        return inferences.stream().distinct().collect(Collectors.toList());
    }

    public Map<String, Object> getKnowledgeStats(String sessionId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        
        List<ConceptNode> sessionConcepts = knowledgeGraphService.getConceptsBySessionId(sessionId);
        stats.put("sessionConcepts", sessionConcepts.size());
        
        long totalConcepts = knowledgeGraphService.getAllConcepts().size();
        stats.put("totalConcepts", totalConcepts);
        
        return stats;
    }
}
