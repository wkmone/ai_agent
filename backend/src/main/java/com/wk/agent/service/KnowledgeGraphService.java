package com.wk.agent.service;

import com.wk.agent.entity.neo4j.ConceptNode;
import com.wk.agent.repository.neo4j.ConceptNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class KnowledgeGraphService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphService.class);

    @Autowired
    private ConceptNodeRepository conceptNodeRepository;

    public ConceptNode createConcept(String name, String category, String definition, String sessionId) {
        log.debug("创建概念节点: name={}, category={}", name, category);

        ConceptNode concept = new ConceptNode();
        concept.setName(name);
        concept.setCategory(category);
        concept.setDefinition(definition);
        concept.setSessionId(sessionId);
        concept.setImportance(0.5);
        concept.setConfidence(0.5);
        concept.setCreatedAt(LocalDateTime.now());
        concept.setUpdatedAt(LocalDateTime.now());
        concept.setAccessCount(0);

        return conceptNodeRepository.save(concept);
    }

    public Optional<ConceptNode> findConceptByName(String name) {
        return conceptNodeRepository.findByName(name);
    }

    public List<ConceptNode> searchConcepts(String keyword) {
        return conceptNodeRepository.findByNameContaining(keyword);
    }

    public List<ConceptNode> getConceptsByCategory(String category) {
        return conceptNodeRepository.findByCategory(category);
    }

    public List<ConceptNode> getConceptsBySessionId(String sessionId) {
        return conceptNodeRepository.findBySessionId(sessionId);
    }

    public void createRelation(String sourceName, String targetName, String relationType, Double weight) {
        log.debug("创建概念关系: {} -[{}]-> {}", sourceName, relationType, targetName);

        Optional<ConceptNode> sourceOpt = conceptNodeRepository.findByName(sourceName);
        Optional<ConceptNode> targetOpt = conceptNodeRepository.findByName(targetName);

        if (sourceOpt.isPresent() && targetOpt.isPresent()) {
            ConceptNode source = sourceOpt.get();
            ConceptNode target = targetOpt.get();
            source.addRelatedConcept(target, relationType, weight);
            conceptNodeRepository.save(source);
        } else {
            log.warn("无法创建关系: 源概念或目标概念不存在");
        }
    }

    public List<ConceptNode> getRelatedConcepts(String name) {
        return conceptNodeRepository.findRelatedConcepts(name);
    }

    public List<ConceptNode> getRelatedConceptsInDepth(String name, int depth) {
        int maxDepth = Math.min(Math.max(depth, 1), 5);
        return conceptNodeRepository.findRelatedConceptsInDepth(name);
    }

    public void updateConceptImportance(String name, Double importance) {
        Optional<ConceptNode> conceptOpt = conceptNodeRepository.findByName(name);
        if (conceptOpt.isPresent()) {
            ConceptNode concept = conceptOpt.get();
            concept.setImportance(importance);
            concept.setUpdatedAt(LocalDateTime.now());
            conceptNodeRepository.save(concept);
        }
    }

    public void incrementAccessCount(String name) {
        Optional<ConceptNode> conceptOpt = conceptNodeRepository.findByName(name);
        if (conceptOpt.isPresent()) {
            ConceptNode concept = conceptOpt.get();
            concept.setAccessCount(concept.getAccessCount() + 1);
            concept.setUpdatedAt(LocalDateTime.now());
            conceptNodeRepository.save(concept);
        }
    }

    public void deleteConcept(String name) {
        Optional<ConceptNode> conceptOpt = conceptNodeRepository.findByName(name);
        conceptOpt.ifPresent(concept -> conceptNodeRepository.delete(concept));
    }

    public void deleteConceptsBySessionId(String sessionId) {
        conceptNodeRepository.deleteBySessionId(sessionId);
    }

    public long deleteLowImportanceConcepts(Double threshold) {
        return conceptNodeRepository.deleteByImportanceLessThan(threshold);
    }

    public long getTotalConceptCount() {
        return conceptNodeRepository.countAll();
    }

    public Double getAverageImportanceByCategory(String category) {
        return conceptNodeRepository.getAverageImportanceByCategory(category);
    }

    public ConceptNode createOrUpdateConcept(String name, String category, String definition, String sessionId) {
        Optional<ConceptNode> existingConcept = conceptNodeRepository.findByName(name);

        if (existingConcept.isPresent()) {
            ConceptNode concept = existingConcept.get();
            if (definition != null) concept.setDefinition(definition);
            if (category != null) concept.setCategory(category);
            concept.setUpdatedAt(LocalDateTime.now());
            concept.setAccessCount(concept.getAccessCount() + 1);
            return conceptNodeRepository.save(concept);
        } else {
            return createConcept(name, category, definition, sessionId);
        }
    }

    public List<ConceptNode> findImportantConcepts(Double threshold) {
        return conceptNodeRepository.findByImportanceGreaterThan(threshold);
    }

    public List<ConceptNode> getAllConcepts() {
        return conceptNodeRepository.findAll();
    }

    public List<java.util.Map<String, Object>> getAllRelations(int limit) {
        List<java.util.Map<String, Object>> relations = new ArrayList<>();
        List<ConceptNode> allConcepts = conceptNodeRepository.findAll();
        
        int count = 0;
        for (ConceptNode concept : allConcepts) {
            if (count >= limit) break;
            
            if (concept.getRelatedConcepts() != null) {
                for (var relation : concept.getRelatedConcepts()) {
                    if (count >= limit) break;
                    
                    java.util.Map<String, Object> edge = new HashMap<>();
                    edge.put("source", concept.getName());
                    edge.put("target", relation.getTarget().getName());
                    edge.put("relationType", relation.getRelationType());
                    edge.put("weight", relation.getWeight());
                    relations.add(edge);
                    count++;
                }
            }
        }
        
        return relations;
    }

    public long getTotalRelationCount() {
        long count = 0;
        List<ConceptNode> allConcepts = conceptNodeRepository.findAll();
        for (ConceptNode concept : allConcepts) {
            if (concept.getRelatedConcepts() != null) {
                count += concept.getRelatedConcepts().size();
            }
        }
        return count;
    }
}
