package com.wk.agent.core;

import com.wk.agent.advisor.MyLoggerAdvisor;
import com.wk.agent.entity.AgentConfig;
import com.wk.agent.entity.ModelConfig;
import com.wk.agent.factory.DynamicChatClientFactory;
import com.wk.agent.impl.*;
import com.wk.agent.service.ModelConfigService;
import com.wk.agent.service.MultiLayerMemoryManager;
import com.wk.agent.service.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AgentFactory {
    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    @Autowired
    private ChatMemory chatMemory;

    private final ApplicationContext applicationContext;
    private final ModelConfigService modelConfigService;
    private final Map<Long, AbstractAgent> agentCache = new ConcurrentHashMap<>();

    public AgentFactory(ApplicationContext applicationContext, ModelConfigService modelConfigService) {
        this.applicationContext = applicationContext;
        this.modelConfigService = modelConfigService;
    }

    public AbstractAgent createAgent(AgentConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Agent配置不能为空");
        }

        Long agentId = config.getId();
        if (agentCache.containsKey(agentId)) {
            return agentCache.get(agentId);
        }

        AbstractAgent agent = createAgentByType(config);
        if (agent != null) {
            
            try {
                MultiLayerMemoryManager memoryManager = applicationContext.getBean(MultiLayerMemoryManager.class);
                agent.memoryManager = memoryManager;
            } catch (Exception e) {
                log.warn("无法注入MemoryManager: {}", e.getMessage());
            }
            
            try {
                ToolCallback[] toolCallbacks = applicationContext.getBeansOfType(ToolCallback.class)
                    .values()
                    .toArray(new ToolCallback[0]);
                agent.toolCallbacks = toolCallbacks;
            } catch (Exception e) {
                log.warn("无法注入ToolCallbacks: {}", e.getMessage());
            }
            
            try {
                RagService ragService = applicationContext.getBean(RagService.class);
                agent.ragService = ragService;
                log.info("RagService已注入到Agent: {}", config.getName());
            } catch (Exception e) {
                log.warn("无法注入RagService: {}", e.getMessage());
            }
            
            applyConfigToAgent(agent, config);
            agent.initialize();
            agentCache.put(agentId, agent);
            log.info("创建Agent成功: id={}, type={}, name={}", 
                agentId, config.getBaseAgentType(), config.getName());
        }

        return agent;
    }

    private AbstractAgent createAgentByType(AgentConfig config) {
        String id = String.valueOf(config.getId());
        String name = config.getName();
        String description = config.getDescription() != null ? config.getDescription() : "";
        String baseAgentType = config.getBaseAgentType();
        
        DynamicChatClientFactory dynamicChatClientFactory = applicationContext.getBean(DynamicChatClientFactory.class);
        org.springframework.ai.chat.memory.ChatMemory chatMemory = applicationContext.getBean(org.springframework.ai.chat.memory.ChatMemory.class);

        return switch (baseAgentType) {
            case "SimpleAgent" -> new SimpleAgent(id, name, description, config, dynamicChatClientFactory, chatMemory);
            case "ReActAgent" -> new ReActAgent(id, name, description);
            case "ReflectionAgent" -> new ReflectionAgent(id, name, description);
            case "PlanAndSolveAgent" -> new PlanAndSolveAgent(id, name, description);
            case "FunctionCallAgent" -> new FunctionCallAgent(id, name, description);
            default -> {
                log.warn("未知的Agent类型: {}, 使用SimpleAgent", baseAgentType);
                yield new SimpleAgent(id, name, description, config, dynamicChatClientFactory, chatMemory);
            }
        };
    }

    private void applyConfigToAgent(AbstractAgent agent, AgentConfig config) {
        log.info("应用配置到Agent: {}", config.getName());
        
        if (config.getModelConfigId() != null) {
            try {
                ModelConfig modelConfig = modelConfigService.getById(config.getModelConfigId());
                if (modelConfig != null) {
                    if (modelConfig.getModelName() != null) {
                        agent.setModelName(modelConfig.getModelName());
                        log.info("设置模型: {}", modelConfig.getModelName());
                    }
                    if (config.getTemperature() == null && modelConfig.getTemperature() != null) {
                        agent.setTemperature(modelConfig.getTemperature());
                        log.info("使用模型配置的温度: {}", modelConfig.getTemperature());
                    }
                } else {
                    log.warn("未找到模型配置, modelConfigId={}", config.getModelConfigId());
                }
            } catch (Exception e) {
                log.error("查询模型配置时出错: {}", e.getMessage());
            }
        }
        
        if (config.getTemperature() != null) {
            agent.setTemperature(config.getTemperature());
            log.info("设置温度: {}", config.getTemperature());
        }
        
        if (config.getKnowledgeBaseId() != null) {
            agent.setKnowledgeBaseId(config.getKnowledgeBaseId());
            log.info("设置知识库ID: {}", config.getKnowledgeBaseId());
        }
        
        if (config.getTools() != null) {
            agent.setTools(config.getTools());
            log.info("设置工具: {}", config.getTools());
        }
    }

    public AbstractAgent getAgent(Long agentId) {
        return agentCache.get(agentId);
    }

    public void removeAgent(Long agentId) {
        agentCache.remove(agentId);
        log.info("从缓存中移除Agent: id={}", agentId);
    }

    public void clearCache() {
        agentCache.clear();
        log.info("清除Agent缓存");
    }
}