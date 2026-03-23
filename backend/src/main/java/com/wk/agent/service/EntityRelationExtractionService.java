package com.wk.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wk.agent.entity.neo4j.ConceptNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EntityRelationExtractionService {

    private static final Logger log = LoggerFactory.getLogger(EntityRelationExtractionService.class);

    @Autowired
    private KnowledgeGraphService knowledgeGraphService;

    @Autowired(required = false)
    private ChatClient chatClient;

    @Autowired
    private ObjectMapper objectMapper;

    private static final Map<String, String[]> RELATION_PATTERNS = Map.of(
        "is_a", new String[]{"是(.+?)的一种", "是一种(.+?)", "属于(.+?)类型"},
        "part_of", new String[]{"是(.+?)的一部分", "属于(.+?)的组成部分", "包含在(.+?)中"},
        "related_to", new String[]{"与(.+?)相关", "和(.+?)有关", "类似于(.+?)"},
        "causes", new String[]{"导致(.+?)", "引起(.+?)", "造成(.+?)"},
        "has_property", new String[]{"具有(.+?)属性", "拥有(.+?)特性", "特点是(.+?)"},
        "used_for", new String[]{"用于(.+?)", "用来(.+?)", "目的是(.+?)"}
    );

    private static final String[] ENTITY_PATTERNS = {
        "([\\u4e00-\\u9fa5]{2,10}(?:技术|方法|系统|模型|算法|框架|工具|语言|平台|应用|概念|理论|原理))"
    };

    private static final Set<String> STOP_WORDS = Set.of(
        "的", "是", "在", "有", "和", "与", "或", "等", "这", "那", "了", "着", "过",
        "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did", "will", "would", "could", "should"
    );

    private static final String EXTRACTION_SYSTEM_PROMPT = """
你是一个专业的知识图谱构建助手。你的任务是从文本中提取实体和关系。

请按照以下JSON格式输出：
{
    "entities": [
        {
            "name": "实体名称",
            "type": "实体类型（如：技术、概念、人物、组织、事件等）",
            "description": "简要描述"
        }
    ],
    "relations": [
        {
            "source": "源实体",
            "target": "目标实体",
            "type": "关系类型",
            "confidence": 0.0-1.0之间的置信度
        }
    ]
}

关系类型包括：
- is_a: 是...的一种（继承关系）
- part_of: 是...的一部分（组成关系）
- related_to: 与...相关（关联关系）
- causes: 导致...（因果关系）
- has_property: 具有...属性（属性关系）
- used_for: 用于...（用途关系）
- depends_on: 依赖于...（依赖关系）
- implements: 实现...（实现关系）
- similar_to: 类似于...（相似关系）

注意：
1. 只提取明确提到的实体和关系
2. 实体名称要准确，不要添加不存在的内容
3. 置信度根据文本中关系的明确程度判断
4. 如果没有找到实体或关系，返回空数组
""";

    public List<String> extractEntities(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> entities = new LinkedHashSet<>();

        for (String pattern : ENTITY_PATTERNS) {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(text);
            while (m.find()) {
                String entity = m.group(1);
                if (!STOP_WORDS.contains(entity.toLowerCase())) {
                    entities.add(entity);
                }
            }
        }

        String[] words = text.split("[\\s,，。.!！?？;；:：\"\"''()（）【】\\[\\]]+");
        for (String word : words) {
            if (word.length() >= 2 && word.length() <= 10) {
                if (!STOP_WORDS.contains(word.toLowerCase())) {
                    if (isLikelyEntity(word)) {
                        entities.add(word);
                    }
                }
            }
        }

        return new ArrayList<>(entities);
    }

    private boolean isLikelyEntity(String word) {
        if (word.matches(".*[\\u4e00-\\u9fa5]{2,}.*")) {
            return true;
        }
        if (word.matches("[A-Z][a-z]+(?:[A-Z][a-z]+)*")) {
            return true;
        }
        if (word.matches("[A-Z]{2,}")) {
            return true;
        }
        return false;
    }

    public List<Map<String, String>> extractRelations(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, String>> relations = new ArrayList<>();

        for (Map.Entry<String, String[]> entry : RELATION_PATTERNS.entrySet()) {
            String relationType = entry.getKey();
            String[] patterns = entry.getValue();

            for (String pattern : patterns) {
                Pattern p = Pattern.compile(pattern);
                Matcher m = p.matcher(text);
                while (m.find()) {
                    String target = m.group(1);
                    if (target != null && !STOP_WORDS.contains(target.toLowerCase())) {
                        Map<String, String> relation = new HashMap<>();
                        relation.put("relationType", relationType);
                        relation.put("target", target);
                        relations.add(relation);
                    }
                }
            }
        }

        return relations;
    }

    public Map<String, Object> extractWithLLM(String text) {
        if (chatClient == null) {
            log.warn("ChatClient 未配置，使用规则方法提取");
            return extractWithRules(text);
        }

        try {
            log.debug("使用 LLM 提取实体关系");
            
            String userPrompt = EXTRACTION_SYSTEM_PROMPT + "\n\n请从以下文本中提取实体和关系：\n\n" + text;
            
            String content = chatClient.prompt()
                .user(userPrompt)
                .call()
                .content();
            
            return parseLLMExtractionResult(content);
        } catch (Exception e) {
            log.error("LLM 提取失败，回退到规则方法: {}", e.getMessage());
            return extractWithRules(text);
        }
    }

    private Map<String, Object> extractWithRules(String text) {
        Map<String, Object> result = new HashMap<>();
        
        List<String> entityNames = extractEntities(text);
        List<Map<String, Object>> entities = new ArrayList<>();
        for (String name : entityNames) {
            Map<String, Object> entity = new HashMap<>();
            entity.put("name", name);
            entity.put("type", "unknown");
            entity.put("description", "");
            entities.add(entity);
        }
        
        List<Map<String, String>> rawRelations = extractRelations(text);
        List<Map<String, Object>> relations = new ArrayList<>();
        for (Map<String, String> rawRel : rawRelations) {
            Map<String, Object> relation = new HashMap<>();
            relation.put("source", "");
            relation.put("target", rawRel.get("target"));
            relation.put("type", rawRel.get("relationType"));
            relation.put("confidence", 0.5);
            relations.add(relation);
        }
        
        result.put("entities", entities);
        result.put("relations", relations);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseLLMExtractionResult(String content) {
        Map<String, Object> result = new HashMap<>();
        result.put("entities", new ArrayList<>());
        result.put("relations", new ArrayList<>());

        if (content == null || content.isEmpty()) {
            return result;
        }

        try {
            String jsonContent = content;
            if (content.contains("```json")) {
                jsonContent = content.substring(content.indexOf("```json") + 7);
                jsonContent = jsonContent.substring(0, jsonContent.indexOf("```"));
            } else if (content.contains("```")) {
                jsonContent = content.substring(content.indexOf("```") + 3);
                jsonContent = jsonContent.substring(0, jsonContent.indexOf("```"));
            }
            
            jsonContent = jsonContent.trim();
            
            Map<String, Object> parsed = objectMapper.readValue(jsonContent, new TypeReference<Map<String, Object>>() {});
            
            if (parsed.containsKey("entities")) {
                result.put("entities", parsed.get("entities"));
            }
            if (parsed.containsKey("relations")) {
                result.put("relations", parsed.get("relations"));
            }
        } catch (Exception e) {
            log.error("解析 LLM 结果失败: {}", e.getMessage());
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    public void extractAndStoreKnowledge(String text, String sessionId) {
        log.debug("从文本提取知识: sessionId={}", sessionId);

        Map<String, Object> extractionResult = extractWithLLM(text);
        
        List<Map<String, Object>> entities = (List<Map<String, Object>>) extractionResult.get("entities");
        List<Map<String, Object>> relations = (List<Map<String, Object>>) extractionResult.get("relations");

        if (entities != null) {
            for (Map<String, Object> entityMap : entities) {
                String name = (String) entityMap.get("name");
                String type = (String) entityMap.getOrDefault("type", "extracted");
                String description = (String) entityMap.getOrDefault("description", "");

                if (name != null && !name.isEmpty()) {
                    Optional<ConceptNode> existing = knowledgeGraphService.findConceptByName(name);
                    if (existing.isEmpty()) {
                        knowledgeGraphService.createConcept(name, type, description, sessionId);
                        log.debug("创建概念: {} ({})", name, type);
                    } else {
                        knowledgeGraphService.incrementAccessCount(name);
                    }
                }
            }
        }

        if (relations != null) {
            for (Map<String, Object> relationMap : relations) {
                String source = (String) relationMap.get("source");
                String target = (String) relationMap.get("target");
                String type = (String) relationMap.getOrDefault("type", "related_to");
                Double confidence = 0.5;
                Object confObj = relationMap.get("confidence");
                if (confObj instanceof Number) {
                    confidence = ((Number) confObj).doubleValue();
                }

                if (source != null && target != null && !source.isEmpty() && !target.isEmpty()) {
                    knowledgeGraphService.createRelation(source, target, type, confidence);
                    log.debug("创建关系: {} -[{}]-> {} (置信度: {})", source, type, target, confidence);
                }
            }
        }
    }

    public Map<String, Object> analyzeText(String text, String sessionId) {
        Map<String, Object> result = extractWithLLM(text);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities = (List<Map<String, Object>>) result.get("entities");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> relations = (List<Map<String, Object>>) result.get("relations");

        result.put("entityCount", entities != null ? entities.size() : 0);
        result.put("relationCount", relations != null ? relations.size() : 0);

        extractAndStoreKnowledge(text, sessionId);

        return result;
    }

    public List<String> extractKeywords(String text, int topN) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, Integer> wordFreq = new HashMap<>();

        String[] words = text.split("[\\s,，。.!！?？;；:：\"\"''()（）【】\\[\\]]+");
        for (String word : words) {
            word = word.trim();
            if (word.length() >= 2 && !STOP_WORDS.contains(word.toLowerCase())) {
                wordFreq.put(word, wordFreq.getOrDefault(word, 0) + 1);
            }
        }

        return wordFreq.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(topN)
            .map(Map.Entry::getKey)
            .toList();
    }

    public String summarizeKnowledge(String sessionId) {
        List<ConceptNode> concepts = knowledgeGraphService.getConceptsBySessionId(sessionId);
        
        if (concepts.isEmpty()) {
            return "暂无知识记录";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("知识图谱摘要:\n");
        sb.append("总概念数: ").append(concepts.size()).append("\n");

        Map<String, List<ConceptNode>> byCategory = new HashMap<>();
        for (ConceptNode concept : concepts) {
            String category = concept.getCategory() != null ? concept.getCategory() : "未分类";
            byCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(concept);
        }

        sb.append("\n按类别统计:\n");
        for (Map.Entry<String, List<ConceptNode>> entry : byCategory.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().size()).append("个概念\n");
        }

        return sb.toString();
    }

    public Map<String, Object> extractEntitiesWithTypes(String text) {
        Map<String, Object> result = extractWithLLM(text);
        
        Map<String, List<String>> entitiesByType = new HashMap<>();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities = (List<Map<String, Object>>) result.get("entities");
        
        if (entities != null) {
            for (Map<String, Object> entity : entities) {
                String name = (String) entity.get("name");
                String type = (String) entity.getOrDefault("type", "unknown");
                
                if (name != null) {
                    entitiesByType.computeIfAbsent(type, k -> new ArrayList<>()).add(name);
                }
            }
        }
        
        Map<String, Object> typedResult = new HashMap<>();
        typedResult.put("entitiesByType", entitiesByType);
        typedResult.put("totalEntities", entities != null ? entities.size() : 0);
        
        return typedResult;
    }

    public List<Map<String, Object>> extractComplexRelations(String text) {
        Map<String, Object> result = extractWithLLM(text);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> relations = (List<Map<String, Object>>) result.get("relations");
        
        if (relations == null) {
            return new ArrayList<>();
        }
        
        return relations.stream()
            .filter(r -> r.get("source") != null && r.get("target") != null)
            .toList();
    }
}
