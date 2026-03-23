package com.wk.agent.core;

import com.wk.agent.entity.SessionMemory;
import com.wk.agent.service.MultiLayerMemoryManager;
import com.wk.agent.service.RagService;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public abstract class AbstractAgent {
    @JsonIgnore
    protected Logger log = LoggerFactory.getLogger(getClass());
    protected String id;
    protected String name;
    protected String description;
    protected AgentStatus status;

    @JsonIgnore
    protected ChatClient chatClient;

    @Setter
    protected ChatModel chatModel;

    @JsonIgnore
    @Autowired(required = false)
    protected MultiLayerMemoryManager memoryManager;

    @JsonIgnore
    @Autowired(required = false)
    protected ToolCallback[] toolCallbacks;

    @Setter
    protected RagService ragService;

    protected String modelName;
    protected Double temperature;
    protected Long knowledgeBaseId;
    protected String tools;

    public AbstractAgent(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.status = AgentStatus.INITIALIZED;
    }

    public void setChatClient(ChatClient chatClient) {
        this.chatClient = chatClient;
        log.info("为Agent设置聊天客户端: {} ({})", name, id);
    }

    public abstract void initialize();

    public abstract AgentResult execute(AgentTask task);

    public abstract AgentResult processMessage(String sessionId, String message);

    public AgentStatus getStatus() {
        return status;
    }

    protected void setStatus(AgentStatus status) {
        log.info("Agent状态变更: {} ({}) - {} -> {}", name, id, this.status, status, this.status, status);
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getTools() {
        return tools;
    }

    public void setTools(String tools) {
        this.tools = tools;
    }

    protected void addWorkingMemory(String sessionId, String content, Double importance) {
        if (memoryManager == null) {
            log.warn("MemoryManager未初始化，无法添加工作记忆");
            return;
        }
        memoryManager.addWorkingMemory(sessionId, content, importance);
    }

    protected List<SessionMemory> getWorkingMemories(String sessionId) {
        if (memoryManager == null) {
            log.warn("MemoryManager未初始化，无法获取工作记忆");
            return List.of();
        }
        return memoryManager.getWorkingMemories(sessionId);
    }

    protected void addEpisodicMemory(String sessionId, String content, Double importance, String keywords) {
        if (memoryManager == null) {
            log.warn("MemoryManager未初始化，无法添加情景记忆");
            return;
        }
        memoryManager.addEpisodicMemory(sessionId, content, importance, keywords);
    }

    protected List<SessionMemory> getEpisodicMemories(String sessionId) {
        if (memoryManager == null) {
            log.warn("MemoryManager未初始化，无法获取情景记忆");
            return List.of();
        }
        return memoryManager.getEpisodicMemories(sessionId);
    }

    protected void addSemanticMemory(String sessionId, String content, Double importance, String keywords) {
        if (memoryManager == null) {
            log.warn("MemoryManager未初始化，无法添加语义记忆");
            return;
        }
        memoryManager.addSemanticMemory(sessionId, content, importance, keywords);
    }

    protected List<SessionMemory> getSemanticMemories(String sessionId) {
        if (memoryManager == null) {
            log.warn("MemoryManager未初始化，无法获取语义记忆");
            return List.of();
        }
        return memoryManager.getSemanticMemories(sessionId);
    }

    protected String buildContext(String sessionId, String query) {
        if (memoryManager == null) {
            log.warn("MemoryManager未初始化，无法构建上下文");
            return "";
        }
        return memoryManager.buildContextPrompt(sessionId, query);
    }

    protected String buildKnowledgeBaseContext(String query) {
        if (ragService == null || knowledgeBaseId == null) {
            return "";
        }
        try {
            List<Map<String, Object>> searchResults = ragService.searchWithKnowledgeBase(
                query, knowledgeBaseId, 5, 0.7, false);
            if (searchResults == null || searchResults.isEmpty()) {
                return "";
            }
            StringBuilder context = new StringBuilder();
            context.append("\n\n【知识库检索结果】\n");
            for (int i = 0; i < searchResults.size(); i++) {
                Map<String, Object> result = searchResults.get(i);
                context.append("[").append(i + 1).append("] ");
                context.append(result.get("content")).append("\n\n");
            }
            context.append("【说明】以上是从知识库中检索到的相关信息，请结合这些信息回答用户的问题。\n");
            return context.toString();
        } catch (Exception e) {
            log.warn("获取知识库上下文失败: {}", e.getMessage());
            return "";
        }
    }

    public List<Map<String, String>> getAvailableTools() {
        if (toolCallbacks == null || toolCallbacks.length == 0) {
            return List.of();
        }
        List<Map<String, String>> tools = new ArrayList<>();
        for (ToolCallback callback : toolCallbacks) {
            Map<String, String> tool = new HashMap<>();
            tool.put("name", callback.getToolDefinition().name());
            tool.put("description", callback.getToolDefinition().description());
            tools.add(tool);
        }
        return tools;
    }

    public String getToolDescriptions() {
        if (toolCallbacks == null || toolCallbacks.length == 0) {
            return "暂无可用工具";
        }
        StringBuilder sb = new StringBuilder();
        for (ToolCallback callback : toolCallbacks) {
            sb.append("- ").append(callback.getToolDefinition().name())
              .append(": ").append(callback.getToolDefinition().description()).append("\n");
        }
        return sb.toString();
    }
}
