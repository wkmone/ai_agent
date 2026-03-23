package com.wk.agent.service.impl;

import com.wk.agent.core.AbstractAgent;
import com.wk.agent.core.AgentFactory;
import com.wk.agent.core.AgentResult;
import com.wk.agent.core.AgentTask;
import com.wk.agent.entity.AgentConfig;
import com.wk.agent.impl.CodebaseMaintainer;
import com.wk.agent.service.AgentConfigService;
import com.wk.agent.service.AgentService;
import com.wk.agent.service.MemoryContextBuilderV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AgentServiceImpl implements AgentService {
    private static final Logger log = LoggerFactory.getLogger(AgentServiceImpl.class);
    
    private final List<AbstractAgent> staticAgents;
    private CodebaseMaintainer codebaseMaintainer;
    private final AgentFactory agentFactory;
    private final AgentConfigService agentConfigService;
    
    @Autowired
    private MemoryContextBuilderV2 memoryContextBuilderV2;
    
    @Autowired
    public AgentServiceImpl(ListableBeanFactory beanFactory, AgentFactory agentFactory, AgentConfigService agentConfigService) {
        this.staticAgents = beanFactory.getBeansOfType(AbstractAgent.class)
                .values()
                .stream()
                .toList();
        System.out.println("Loaded static agents: " + staticAgents.stream().map(AbstractAgent::getName).toList());
        
        for (AbstractAgent agent : staticAgents) {
            if (agent instanceof CodebaseMaintainer) {
                this.codebaseMaintainer = (CodebaseMaintainer) agent;
            }
        }
        
        this.agentFactory = agentFactory;
        this.agentConfigService = agentConfigService;
    }
    
    @Override
    public List<AbstractAgent> getAllAgents() {
        List<AbstractAgent> allAgents = new ArrayList<>(staticAgents);
        
        List<AgentConfig> configs = agentConfigService.getAllAgentConfigs();
        for (AgentConfig config : configs) {
            try {
                AbstractAgent agent = agentFactory.createAgent(config);
                if (agent != null) {
                    allAgents.add(agent);
                }
            } catch (Exception e) {
                System.err.println("Failed to create agent from config: " + config.getName());
            }
        }
        
        return allAgents;
    }
    
    @Override
    public AbstractAgent getAgentById(String id) {
        log.info("尝试获取Agent, id={}", id);
        try {
            Long agentId = Long.parseLong(id);
            log.info("解析为Long类型: {}", agentId);
            
            AbstractAgent agent = agentFactory.getAgent(agentId);
            if (agent != null) {
                log.info("从缓存找到Agent: {}", agent.getName());
                return agent;
            }
            log.info("缓存中未找到，从数据库查询配置");
            
            AgentConfig config = agentConfigService.getAgentConfigById(agentId);
            if (config != null) {
                log.info("找到配置，创建Agent: name={}, type={}", config.getName(), config.getBaseAgentType());
                return agentFactory.createAgent(config);
            }
            log.info("数据库中未找到配置，id={}", agentId);
        } catch (NumberFormatException e) {
            log.info("不是数字ID，查找静态Agent");
        } catch (Exception e) {
            log.error("获取Agent时出错", e);
        }
        
        return staticAgents.stream()
                .filter(agent -> agent.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
    
    @Override
    public AbstractAgent getAgentByName(String name) {
        List<AbstractAgent> allAgents = getAllAgents();
        return allAgents.stream()
                .filter(agent -> agent.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
    
    @Override
    public AgentResult executeTask(String agentId, AgentTask task) {
        AbstractAgent agent = getAgentById(agentId);
        if (agent == null) {
            return new AgentResult("Agent not found: " + agentId, false);
        }
        return agent.execute(task);
    }
    
    @Override
    public AgentResult processMessage(String agentId, String sessionId, String message) {
        AbstractAgent agent = getAgentById(agentId);
        if (agent == null) {
            return new AgentResult("Agent not found: " + agentId, false);
        }

        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = "default";
        }

        AgentResult result = agent.processMessage(sessionId, message);

        if (result.isSuccess()) {
            String responseContent = result.getMessage() != null ? result.getMessage() : "";
            memoryContextBuilderV2.saveConversation(sessionId, message, responseContent);
        }

        return result;
    }
    
    @Override
    public void initializeCodebaseMaintainer(String projectName, String codebasePath) {
        if (codebaseMaintainer == null) {
            throw new IllegalStateException("CodebaseMaintainer not found");
        }
        codebaseMaintainer.initialize(projectName, codebasePath);
    }
    
    @Override
    public Map<String, Object> getCodebaseMaintainerStats() {
        if (codebaseMaintainer == null) {
            return Map.of("error", "CodebaseMaintainer not found");
        }
        return codebaseMaintainer.getStats();
    }
    
    @Override
    public String codebaseMaintainerExplore(String target) {
        if (codebaseMaintainer == null) {
            return "CodebaseMaintainer not found";
        }
        return codebaseMaintainer.explore(target);
    }
    
    @Override
    public String codebaseMaintainerAnalyze(String focus) {
        if (codebaseMaintainer == null) {
            return "CodebaseMaintainer not found";
        }
        return codebaseMaintainer.analyze(focus);
    }
    
    @Override
    public String codebaseMaintainerPlanNextSteps() {
        if (codebaseMaintainer == null) {
            return "CodebaseMaintainer not found";
        }
        return codebaseMaintainer.planNextSteps();
    }
}
