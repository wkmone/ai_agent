package com.wk.agent.service;

import com.wk.agent.entity.SessionMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MemoryContextBuilderV2 {

    private static final Logger log = LoggerFactory.getLogger(MemoryContextBuilderV2.class);

    @Autowired
    private MultiLayerMemoryManager multiLayerMemoryManager;

    @Autowired
    private MemoryImportanceCalculator importanceCalculator;

    @Autowired(required = false)
    private VectorSearchService vectorSearchService;

    public String buildContextPrompt(String sessionId, String userQuery) {
        log.debug("构建记忆上下文: sessionId={}, query={}", sessionId, userQuery);

        if (sessionId == null || sessionId.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();

        List<SessionMemory> semanticMemories = multiLayerMemoryManager.getSemanticMemories(sessionId);
        if (!semanticMemories.isEmpty()) {
            context.append("【知识】\n");
            int index = 1;
            for (SessionMemory memory : semanticMemories) {
                if (memory.getImportance() != null && memory.getImportance() >= 0.7) {
                    context.append(index++).append(". ").append(memory.getContent());
                    if (memory.getKeywords() != null && !memory.getKeywords().isEmpty()) {
                        context.append(" [").append(memory.getKeywords()).append("]");
                    }
                    context.append("\n");
                    if (index > 5) break;
                }
            }
            context.append("\n");
        }

        List<SessionMemory> episodicMemories = multiLayerMemoryManager.getEpisodicMemories(sessionId);
        if (!episodicMemories.isEmpty()) {
            context.append("【相关事件】\n");
            int index = 1;
            for (SessionMemory memory : episodicMemories) {
                if (memory.getImportance() != null && memory.getImportance() >= 0.6) {
                    context.append(index++).append(". ").append(memory.getContent());
                    if (memory.getCreatedAt() != null) {
                        context.append(" [").append(memory.getCreatedAt().toLocalDate()).append("]");
                    }
                    context.append("\n");
                    if (index > 5) break;
                }
            }
            context.append("\n");
        }

        List<SessionMemory> workingMemories = multiLayerMemoryManager.getWorkingMemories(sessionId);
        if (!workingMemories.isEmpty()) {
            context.append("【当前上下文】\n");
            for (SessionMemory memory : workingMemories) {
                context.append("• ").append(memory.getContent()).append("\n");
            }
            context.append("\n");
        }

        if (vectorSearchService != null && userQuery != null && !userQuery.isEmpty()) {
            try {
                List<Map<String, Object>> similarMemories = vectorSearchService.searchSimilar(userQuery, sessionId, 5);
                if (!similarMemories.isEmpty()) {
                    context.append("【语义相关记忆】\n");
                    int index = 1;
                    for (Map<String, Object> similarMemory : similarMemories) {
                        String content = (String) similarMemory.get("content");
                        Double score = (Double) similarMemory.get("score");
                        if (content != null) {
                            context.append(index++).append(". ").append(content);
                            if (score != null) {
                                context.append(" [相似度: ").append(String.format("%.2f", score)).append("]");
                            }
                            context.append("\n");
                            if (index > 5) break;
                        }
                    }
                    context.append("\n");
                    log.debug("已添加 {} 条语义相关记忆到上下文", similarMemories.size());
                }
            } catch (Exception e) {
                log.warn("向量搜索失败，跳过语义相关记忆: {}", e.getMessage());
            }
        }

        if (context.length() > 0) {
            context.insert(0, "请参考以下记忆上下文回答用户问题：\n\n");
        }

        return context.toString();
    }

    public void saveConversation(String sessionId, String userMessage, String assistantResponse) {
        log.debug("保存对话到记忆: sessionId={}", sessionId);

        String combinedContent = "用户: " + userMessage + "\n助手: " + assistantResponse;

        double importance = importanceCalculator.calculateImportanceScore(combinedContent);

        multiLayerMemoryManager.addWorkingMemory(sessionId, combinedContent, importance);

        if (importance >= 0.6) {
            multiLayerMemoryManager.addEpisodicMemory(sessionId, combinedContent, importance, importanceCalculator.extractKeywords(userMessage));
        }

        if (importance >= 0.7 && assistantResponse.length() > 100) {
            multiLayerMemoryManager.addSemanticMemory(sessionId, importanceCalculator.extractKnowledge(assistantResponse), importance, importanceCalculator.extractKeywords(assistantResponse));
        }
    }
}
