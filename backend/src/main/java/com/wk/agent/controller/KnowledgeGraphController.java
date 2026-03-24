package com.wk.agent.controller;

import com.wk.agent.entity.neo4j.ConceptNode;
import com.wk.agent.service.EntityRelationExtractionService;
import com.wk.agent.service.KnowledgeGraphEnhancer;
import com.wk.agent.service.KnowledgeGraphService;
import com.wk.agent.service.KnowledgeReasoningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/knowledge-graph")
@Tag(name = "知识图谱管理", description = "知识图谱相关接口")
public class KnowledgeGraphController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphController.class);

    @Autowired
    private KnowledgeGraphService knowledgeGraphService;

    @Autowired
    private KnowledgeReasoningService knowledgeReasoningService;

    @Autowired
    private EntityRelationExtractionService entityRelationExtractionService;

    @Autowired(required = false)
    private KnowledgeGraphEnhancer knowledgeGraphEnhancer;

    @PostMapping("/concepts")
    @Operation(summary = "创建概念")
    public ResponseEntity<Map<String, Object>> createConcept(
            @RequestParam String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String definition,
            @RequestParam(required = false) String sessionId) {
        try {
            ConceptNode concept = knowledgeGraphService.createConcept(name, category, definition, sessionId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "concept", concept
            ));
        } catch (Exception e) {
            log.error("创建概念失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/concepts/{name}")
    @Operation(summary = "获取概念")
    public ResponseEntity<Map<String, Object>> getConcept(@PathVariable String name) {
        Optional<ConceptNode> concept = knowledgeGraphService.findConceptByName(name);
        if (concept.isPresent()) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "concept", concept.get()
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "概念不存在"
            ));
        }
    }

    @GetMapping("/concepts")
    @Operation(summary = "获取所有概念")
    public ResponseEntity<Map<String, Object>> getAllConcepts() {
        List<ConceptNode> concepts = knowledgeGraphService.getAllConcepts();
        return ResponseEntity.ok(Map.of(
            "success", true,
            "concepts", concepts,
            "total", concepts.size()
        ));
    }

    @GetMapping("/concepts/session/{sessionId}")
    @Operation(summary = "获取会话相关概念")
    public ResponseEntity<Map<String, Object>> getSessionConcepts(@PathVariable String sessionId) {
        List<ConceptNode> concepts = knowledgeGraphService.getConceptsBySessionId(sessionId);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "concepts", concepts,
            "total", concepts.size()
        ));
    }

    @DeleteMapping("/concepts/{name}")
    @Operation(summary = "删除概念")
    public ResponseEntity<Map<String, Object>> deleteConcept(@PathVariable String name) {
        knowledgeGraphService.deleteConcept(name);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "概念已删除"
        ));
    }

    @PostMapping("/relations")
    @Operation(summary = "创建关系")
    public ResponseEntity<Map<String, Object>> createRelation(
            @RequestParam String source,
            @RequestParam String target,
            @RequestParam String relationType,
            @RequestParam(required = false, defaultValue = "0.5") Double confidence) {
        try {
            knowledgeGraphService.createRelation(source, target, relationType, confidence);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "关系已创建"
            ));
        } catch (Exception e) {
            log.error("创建关系失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/concepts/{name}/related")
    @Operation(summary = "获取相关概念")
    public ResponseEntity<Map<String, Object>> getRelatedConcepts(@PathVariable String name) {
        List<ConceptNode> related = knowledgeGraphService.getRelatedConcepts(name);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "relatedConcepts", related,
            "total", related.size()
        ));
    }

    @PostMapping("/extract")
    @Operation(summary = "从文本提取知识")
    public ResponseEntity<Map<String, Object>> extractKnowledge(
            @RequestParam String text,
            @RequestParam(required = false) String sessionId) {
        try {
            Map<String, Object> result = entityRelationExtractionService.analyzeText(text, sessionId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "result", result
            ));
        } catch (Exception e) {
            log.error("提取知识失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/reasoning/transitive")
    @Operation(summary = "传递推理")
    public ResponseEntity<Map<String, Object>> performTransitiveReasoning(@RequestParam String conceptName) {
        List<Map<String, Object>> result = knowledgeReasoningService.inferTransitiveRelations(conceptName, "RELATED_TO");
        return ResponseEntity.ok(Map.of(
            "success", true,
            "inferences", result
        ));
    }

    @PostMapping("/reasoning/similar")
    @Operation(summary = "相似概念查找")
    public ResponseEntity<Map<String, Object>> findSimilarConcepts(
            @RequestParam String conceptName,
            @RequestParam(required = false, defaultValue = "0.5") double threshold) {
        List<Map<String, Object>> result = knowledgeReasoningService.findSimilarConcepts(conceptName, threshold);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "similarConcepts", result
        ));
    }

    @PostMapping("/reasoning/hierarchical")
    @Operation(summary = "层次推理")
    public ResponseEntity<Map<String, Object>> performHierarchicalReasoning(@RequestParam String conceptName) {
        List<Map<String, Object>> result = knowledgeReasoningService.inferHierarchy(conceptName);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "hierarchy", result
        ));
    }

    @GetMapping("/enhance")
    @Operation(summary = "增强上下文")
    public ResponseEntity<Map<String, Object>> enhanceContext(
            @RequestParam String query,
            @RequestParam(required = false) String sessionId) {
        if (knowledgeGraphEnhancer == null) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "知识图谱增强服务不可用"
            ));
        }
        String enhancedContext = knowledgeGraphEnhancer.enhanceContext(sessionId, query);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "enhancedContext", enhancedContext
        ));
    }

    @GetMapping("/stats")
    @Operation(summary = "获取知识图谱统计")
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestParam(required = false) String sessionId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        
        List<ConceptNode> allConcepts = knowledgeGraphService.getAllConcepts();
        stats.put("totalConcepts", allConcepts.size());
        
        if (sessionId != null && !sessionId.isEmpty()) {
            List<ConceptNode> sessionConcepts = knowledgeGraphService.getConceptsBySessionId(sessionId);
            stats.put("sessionConcepts", sessionConcepts.size());
        }
        
        if (knowledgeGraphEnhancer != null && sessionId != null) {
            stats.putAll(knowledgeGraphEnhancer.getKnowledgeStats(sessionId));
        }
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "stats", stats
        ));
    }

    @GetMapping("/full")
    @Operation(summary = "获取完整图谱数据")
    public ResponseEntity<Map<String, Object>> getFullGraph(
            @RequestParam(required = false, defaultValue = "100") Integer nodeLimit,
            @RequestParam(required = false, defaultValue = "200") Integer edgeLimit) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        List<ConceptNode> allConcepts = knowledgeGraphService.getAllConcepts();
        List<Map<String, Object>> nodes = allConcepts.stream()
            .limit(nodeLimit)
            .map(node -> {
                Map<String, Object> nodeMap = new HashMap<>();
                nodeMap.put("id", node.getName());
                nodeMap.put("label", node.getName());
                nodeMap.put("category", node.getCategory());
                nodeMap.put("definition", node.getDefinition());
                nodeMap.put("importance", node.getImportance());
                nodeMap.put("accessCount", node.getAccessCount());
                return nodeMap;
            })
            .collect(Collectors.toList());
        
        List<Map<String, Object>> edges = knowledgeGraphService.getAllRelations(edgeLimit);
        
        result.put("nodes", nodes);
        result.put("edges", edges);
        
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/clear")
    @Operation(summary = "清空知识图谱")
    public ResponseEntity<Map<String, Object>> clearKnowledgeGraph() {
        List<ConceptNode> allConcepts = knowledgeGraphService.getAllConcepts();
        for (ConceptNode concept : allConcepts) {
            knowledgeGraphService.deleteConcept(concept.getName());
        }
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "知识图谱已清空"
        ));
    }
}
