package com.wk.agent.service;

import com.wk.agent.core.AbstractAgent;
import com.wk.agent.core.AgentResult;
import com.wk.agent.core.AgentTask;

import java.util.List;
import java.util.Map;

public interface AgentService {
    List<AbstractAgent> getAllAgents();
    AbstractAgent getAgentById(String id);
    AbstractAgent getAgentByName(String name);
    AgentResult executeTask(String agentId, AgentTask task);
    AgentResult processMessage(String agentId, String sessionId, String message);
    
    void initializeCodebaseMaintainer(String projectName, String codebasePath);
    Map<String, Object> getCodebaseMaintainerStats();
    String codebaseMaintainerExplore(String target);
    String codebaseMaintainerAnalyze(String focus);
    String codebaseMaintainerPlanNextSteps();
}
