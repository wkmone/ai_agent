package com.wk.agent.impl;

import com.wk.agent.advisor.MyLoggerAdvisor;
import com.wk.agent.core.AbstractAgent;
import com.wk.agent.core.AgentResult;
import com.wk.agent.core.AgentStatus;
import com.wk.agent.core.AgentTask;
import com.wk.agent.entity.AgentConfig;
import com.wk.agent.factory.DynamicChatClientFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.OpenAiChatOptions;

public class SimpleAgent extends AbstractAgent {

    private final ChatMemory chatMemory;
    private final DynamicChatClientFactory dynamicChatClientFactory;
    private final Long modelConfigId;

    public SimpleAgent(String id, String name, String description, AgentConfig config, DynamicChatClientFactory dynamicChatClientFactory, ChatMemory chatMemory) {
        super(id, name, description);
        this.modelConfigId = config.getModelConfigId();
        this.dynamicChatClientFactory = dynamicChatClientFactory;
        this.chatMemory = chatMemory;
    }
    
    @Override
    public void initialize() {
        log.info("初始化SimpleAgent: {} ({})", name, id);
        setStatus(AgentStatus.RUNNING);
        setChatModel(this.dynamicChatClientFactory.createChatModel(this.modelConfigId));
        log.info("SimpleAgent初始化完成: {} ({})", name, id);
    }
    
    @Override
    public AgentResult execute(AgentTask task) {
        log.info("执行SimpleAgent任务: {} ({})", name, id);
        log.info("任务内容: {}", task.getTaskContent());
        
        if (chatClient == null) {
            log.error("SimpleAgent聊天客户端未初始化: {} ({})", name, id);
            return new AgentResult("聊天客户端未初始化", false);
        }
        
        try {
            log.info("调用聊天客户端执行任务");
            String response;
            
            OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder();
            if (modelName != null) {
                optionsBuilder.model(modelName);
                log.info("使用模型: {}", modelName);
            }
            if (temperature != null) {
                optionsBuilder.temperature(temperature);
                log.info("使用温度参数: {}", temperature);
            }

            response = chatClientCall(optionsBuilder.build(), task.getTaskContent());
            
            log.info("任务执行成功，响应长度: {}", response.length());
            return new AgentResult(response, true);
        } catch (Exception e) {
            log.error("执行任务时出错: {}", e.getMessage(), e);
            return new AgentResult("执行任务时出错: " + e.getMessage(), false);
        }
    }
    
    @Override
    public AgentResult processMessage(String sessionId, String message) {
        log.info("处理SimpleAgent消息: sessionId={}, message={}", sessionId, message);

        try {
            String knowledgeContext = "";
            if (knowledgeBaseId != null && ragService != null) {
                knowledgeContext = buildKnowledgeBaseContext(message);
                if (!knowledgeContext.isEmpty()) {
                    log.info("已获取知识库上下文，长度: {}", knowledgeContext.length());
                }
            }

            String fullMessage = message + knowledgeContext;
            
            OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder();
            if (modelName != null) {
                optionsBuilder.model(modelName);
                log.info("使用模型: {}", modelName);
            }
            if (temperature != null) {
                optionsBuilder.temperature(temperature);
                log.info("使用温度参数: {}", temperature);
            }
            
            OpenAiChatOptions options = optionsBuilder.build();
            log.info("构建的 Options: {}", ModelOptionsUtils.toJsonString(options));
            
            String response = chatClientCall(options, fullMessage);
            
            log.info("消息处理成功，响应长度: {}", response.length());
            return new AgentResult(response, true);
        } catch (Exception e) {
            log.error("处理消息时出错: {}", e.getMessage(), e);
            return new AgentResult("处理消息时出错: " + e.getMessage(), false);
        }
    }

    private String chatClientCall(OpenAiChatOptions options, String message) {
        if (chatModel == null) {
            throw new IllegalStateException("chatModel未初始化，请确保AgentConfig中设置了modelConfigId");
        }
        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        MyLoggerAdvisor.builder()
                                .showAvailableTools(true)
                                .showSystemMessage(true)
                                .build())
                .build()
                .prompt()
                .user(message)
                .options(options)
                .call()
                .content();
    }
}
