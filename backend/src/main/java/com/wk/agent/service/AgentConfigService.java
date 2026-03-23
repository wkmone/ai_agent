package com.wk.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wk.agent.core.AgentFactory;
import com.wk.agent.entity.AgentConfig;
import com.wk.agent.mapper.AgentConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentConfigService extends ServiceImpl<AgentConfigMapper, AgentConfig> {

    private static final Logger logger = LoggerFactory.getLogger(AgentConfigService.class);

    @Autowired(required = false)
    private AgentFactory agentFactory;

    public List<AgentConfig> getAllAgentConfigs() {
        return list(new LambdaQueryWrapper<AgentConfig>()
                .orderByDesc(AgentConfig::getCreatedAt));
    }

    public AgentConfig getAgentConfigById(Long id) {
        logger.info("查询AgentConfig, id={}", id);
        AgentConfig config = getById(id);
        if (config != null) {
            logger.info("找到AgentConfig: name={}, type={}", config.getName(), config.getBaseAgentType());
        } else {
            logger.warn("未找到AgentConfig, id={}", id);
        }
        return config;
    }

    public AgentConfig createAgentConfig(AgentConfig agentConfig) {
        save(agentConfig);
        return agentConfig;
    }

    public AgentConfig updateAgentConfig(AgentConfig agentConfig) {
        updateById(agentConfig);
        if (agentFactory != null && agentConfig.getId() != null) {
            agentFactory.removeAgent(agentConfig.getId());
        }
        return getById(agentConfig.getId());
    }

    public boolean deleteAgentConfig(Long id) {
        if (agentFactory != null) {
            agentFactory.removeAgent(id);
        }
        return removeById(id);
    }
}
