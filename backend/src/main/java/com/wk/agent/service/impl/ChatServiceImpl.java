package com.wk.agent.service.impl;

import com.wk.agent.config.ToolRegistration;
import com.wk.agent.dto.ChatRequest;
import com.wk.agent.dto.ChatResponse;
import com.wk.agent.entity.Message;
import com.wk.agent.entity.ModelConfig;
import com.wk.agent.service.ChatService;
import com.wk.agent.service.MemoryContextBuilderV2;
import com.wk.agent.service.MessageService;
import com.wk.agent.service.ModelConfigService;
import com.wk.agent.service.MultiLayerMemoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private ToolRegistration toolRegistration;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ModelConfigService modelConfigService;

    @Autowired
    private MemoryContextBuilderV2 memoryContextBuilderV2;

    @Autowired
    private MultiLayerMemoryManager multiLayerMemoryManager;

    @Autowired(required = false)
    private com.wk.agent.service.EntityRelationExtractionService entityRelationExtractionService;

    private ModelConfig cachedConfig;
    private long lastConfigUpdateTime = 0;
    private static final long CONFIG_CACHE_TTL = 60000;

    private ModelConfig getCurrentConfig() {
        long currentTime = System.currentTimeMillis();
        if (cachedConfig == null || currentTime - lastConfigUpdateTime > CONFIG_CACHE_TTL) {
            cachedConfig = modelConfigService.getDefaultConfig();
            lastConfigUpdateTime = currentTime;
        }
        return cachedConfig;
    }

    @Override
    public ChatResponse simpleChat(String message) {
        try {
            ModelConfig config = getCurrentConfig();
            String modelName = config != null ? config.getModelName() : "qwen3";

            String sessionId = "simple_" + UUID.randomUUID().toString().substring(0, 8);

            String response = chatClient.prompt()
                    .user(message)
                    .call()
                    .content();

            memoryContextBuilderV2.saveConversation(sessionId, message, response);

            return ChatResponse.success(response, modelName, 0L);
        } catch (Exception e) {
            log.error("简单聊天失败: {}", e.getMessage());
            return ChatResponse.error("聊天失败: " + e.getMessage());
        }
    }

    @Override
    public ChatResponse chatWithParams(ChatRequest request) {
        try {
            ModelConfig config = getCurrentConfig();
            String modelName = config != null ? config.getModelName() : "qwen3";

            String sessionId = request.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = "params_" + UUID.randomUUID().toString().substring(0, 8);
            }

            String memoryContext = memoryContextBuilderV2.buildContextPrompt(sessionId, request.getMessage());

            var chatCall = chatClient.prompt();

            if (memoryContext != null && !memoryContext.isEmpty()) {
                chatCall = chatCall.system(memoryContext);
            }

            chatCall = chatCall.user(request.getMessage());

            var response = chatCall.call();
            String content = response.content();

            memoryContextBuilderV2.saveConversation(sessionId, request.getMessage(), content);

            return ChatResponse.success(content, modelName, 0L);
        } catch (Exception e) {
            log.error("带参数聊天失败: {}", e.getMessage());
            return ChatResponse.error("聊天失败: " + e.getMessage());
        }
    }

    @Override
    public Flux<String> streamChat(String message) {
        ModelConfig config = getCurrentConfig();

        String sessionId = "stream_" + UUID.randomUUID().toString().substring(0, 8);

        String memoryContext = memoryContextBuilderV2.buildContextPrompt(sessionId, message);

        var chatCall = chatClient.prompt();

        if (memoryContext != null && !memoryContext.isEmpty()) {
            chatCall = chatCall.system(memoryContext);
        }

        return chatCall.user(message).stream().content();
    }

    @Override
    public ChatResponse chatWithHistory(String sessionId, String message) {
        try {
            ModelConfig config = getCurrentConfig();
            String modelName = config != null ? config.getModelName() : "qwen3";

            final String finalSessionId;
            if (sessionId == null || sessionId.isEmpty()) {
                finalSessionId = "history_" + UUID.randomUUID().toString().substring(0, 8);
            } else {
                finalSessionId = sessionId;
            }

            Message userMessage = new Message()
                    .setSessionId(finalSessionId)
                    .setType(1)
                    .setContent(message)
                    .setModelName(modelName);
            messageService.saveMessage(userMessage);

            String memoryContext = memoryContextBuilderV2.buildContextPrompt(finalSessionId, message);

            var chatCall = chatClient.prompt()
                    .advisors(a -> a.param("conversationId", finalSessionId));

            if (memoryContext != null && !memoryContext.isEmpty()) {
                chatCall = chatCall.system(memoryContext);
            }

            chatCall = chatCall.user(message);

            var response = chatCall.call();
            String content = response.content();

            Message assistantMessage = new Message()
                    .setSessionId(finalSessionId)
                    .setType(2)
                    .setContent(content)
                    .setModelName(modelName);
            messageService.saveMessage(assistantMessage);

            memoryContextBuilderV2.saveConversation(finalSessionId, message, content);

            if (entityRelationExtractionService != null) {
                try {
                    String combinedText = "用户: " + message + "\n助手: " + content;
                    entityRelationExtractionService.extractAndStoreKnowledge(combinedText, finalSessionId);
                    log.debug("已从对话中提取知识到知识图谱: sessionId={}", finalSessionId);
                } catch (Exception e) {
                    log.warn("知识提取失败，跳过: {}", e.getMessage());
                }
            }

            return ChatResponse.success(content, modelName, 0L);
        } catch (Exception e) {
            log.error("带历史的聊天失败: {}", e.getMessage());
            return ChatResponse.error("带历史的聊天失败: " + e.getMessage());
        }
    }

    @Override
    public void clearSessionHistory(String sessionId) {
        messageService.deleteBySessionId(sessionId);
        multiLayerMemoryManager.clearAllMemory(sessionId);
        log.info("清除会话历史和记忆: sessionId={}", sessionId);
    }

    public int getActiveSessionsCount() {
        return 0;
    }

    public Map<String, Object> getSessionMemoryStats(String sessionId) {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("working", multiLayerMemoryManager.getWorkingMemories(sessionId).size());
        stats.put("episodic", multiLayerMemoryManager.getEpisodicMemories(sessionId).size());
        stats.put("semantic", multiLayerMemoryManager.getSemanticMemories(sessionId).size());
        return stats;
    }
}