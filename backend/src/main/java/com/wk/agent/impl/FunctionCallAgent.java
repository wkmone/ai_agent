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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;public class FunctionCallAgent extends AbstractAgent {
    
    private final ChatMemory chatMemory;
    private final DynamicChatClientFactory dynamicChatClientFactory;
    private final Long modelConfigId;
    
    public FunctionCallAgent(String id, String name, String description, AgentConfig config, DynamicChatClientFactory dynamicChatClientFactory, ChatMemory chatMemory) {
        super(id, name, description);
        this.chatMemory = chatMemory;
        this.dynamicChatClientFactory = dynamicChatClientFactory;
        this.modelConfigId = config.getModelConfigId();
    }
    
    @Override
    public void initialize() {
        log.info("初始化FunctionCallAgent: {} ({})", name, id);
        setStatus(AgentStatus.RUNNING);
        if (this.modelConfigId != null) {
            setChatModel(this.dynamicChatClientFactory.createChatModel(this.modelConfigId));
        } else {
            log.warn("modelConfigId为null，无法初始化chatModel，请确保AgentConfig中设置了modelConfigId");
        }
        log.info("已注册的工具: {}", getAvailableToolNames());
        log.info("FunctionCallAgent初始化完成: {} ({})", name, id);
    }
    
    private String getAvailableToolNames() {
        if (toolCallbacks == null || toolCallbacks.length == 0) {
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < toolCallbacks.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(toolCallbacks[i].getToolDefinition().name());
        }
        return sb.toString();
    }
    
    @Override
    public AgentResult execute(AgentTask task) {
        log.info("执行FunctionCallAgent任务: {} ({})", name, id);
        log.info("任务内容: {}", task.getTaskContent());
        
        try {
            String message = task.getTaskContent();
            
            log.info("使用Spring AI 2.0自动工具调用机制处理请求");
            
            // 使用Spring AI 2.0的自动工具调用机制
            org.springframework.ai.openai.OpenAiChatOptions.Builder optionsBuilder = org.springframework.ai.openai.OpenAiChatOptions.builder();
            if (modelName != null) {
                optionsBuilder.model(modelName);
                log.info("使用模型: {}", modelName);
            }
            if (temperature != null) {
                optionsBuilder.temperature(temperature);
                log.info("使用温度参数: {}", temperature);
            }
            
            String response = chatClientCall(optionsBuilder.build(), message);
            
            log.info("最终响应生成: {}", response.substring(0, Math.min(100, response.length())) + "...");
            return new AgentResult(response, true);
        } catch (Exception e) {
            log.error("执行函数调用任务时出错: {}", e.getMessage(), e);
            return new AgentResult("执行函数调用任务时出错: " + e.getMessage(), false);
        }
    }
    
    @Override
    public AgentResult processMessage(String sessionId, String message) {
        log.info("处理FunctionCallAgent消息: sessionId={}, message={}", sessionId, message);

        if (chatClient == null) {
            log.error("FunctionCallAgent聊天客户端未初始化: {} ({})", name, id);
            return new AgentResult("聊天客户端未初始化", false);
        }

        try {
            log.info("调用聊天客户端直接生成响应");
            org.springframework.ai.openai.OpenAiChatOptions.Builder optionsBuilder = org.springframework.ai.openai.OpenAiChatOptions.builder();
            if (modelName != null) {
                optionsBuilder.model(modelName);
                log.info("使用模型: {}", modelName);
            }
            if (temperature != null) {
                optionsBuilder.temperature(temperature);
                log.info("使用温度参数: {}", temperature);
            }
            
            String response = chatClientCall(optionsBuilder.build(), message);
            
            log.info("直接响应生成: {}", response.substring(0, Math.min(100, response.length())) + "...");
            return new AgentResult(response, true);
        } catch (Exception e) {
            log.error("处理消息时出错: {}", e.getMessage(), e);
            return new AgentResult("处理消息时出错: " + e.getMessage(), false);
        }
    }
    
    private String chatClientCall(org.springframework.ai.openai.OpenAiChatOptions options, String message) {
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
