package com.wk.agent.domain.service;

import com.wk.agent.domain.aggregate.AgentAggregate;
import com.wk.agent.domain.aggregate.AgentRepository;
import com.wk.agent.domain.entity.AgentTask;
import com.wk.agent.domain.valueobject.AgentResult;

import java.util.List;
import java.util.Optional;

public class AgentDomainService {
    private final AgentRepository agentRepository;

    public AgentDomainService(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    public AgentAggregate createAgent(String name, String type) {
        AgentAggregate agent = new AgentAggregate(name, type);
        return agentRepository.save(agent);
    }

    public Optional<AgentAggregate> getAgentById(String id) {
        return agentRepository.findById(id);
    }

    public Optional<AgentAggregate> getAgentByName(String name) {
        return agentRepository.findByName(name);
    }

    public List<AgentAggregate> getAllAgents() {
        return agentRepository.findAll();
    }

    public AgentResult processMessage(String agentId, String message) {
        Optional<AgentAggregate> agentOpt = agentRepository.findById(agentId);
        if (agentOpt.isEmpty()) {
            return new AgentResult("Agent not found: " + agentId, false);
        }
        AgentAggregate agent = agentOpt.get();
        return agent.processMessage(message);
    }

    public AgentResult executeTask(String agentId, AgentTask task) {
        Optional<AgentAggregate> agentOpt = agentRepository.findById(agentId);
        if (agentOpt.isEmpty()) {
            return new AgentResult("Agent not found: " + agentId, false);
        }
        AgentAggregate agent = agentOpt.get();
        return agent.executeTask(task);
    }

    public void deleteAgent(String agentId) {
        agentRepository.deleteById(agentId);
    }
}