package com.wk.agent.controller;

import com.wk.agent.config.CodebaseProperties;
import com.wk.agent.core.AbstractAgent;
import com.wk.agent.core.AgentResult;
import com.wk.agent.core.AgentTask;
import com.wk.agent.service.AgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {
    
    @Autowired
    private AgentService agentService;
    
    @Autowired
    private CodebaseProperties codebaseProperties;
    
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllAgents() {
        List<AbstractAgent> agents = agentService.getAllAgents();
        List<Map<String, Object>> result = agents.stream()
                .map(agent -> Map.<String, Object>of(
                        "id", agent.getId(),
                        "name", agent.getName(),
                        "description", agent.getDescription(),
                        "status", agent.getStatus().name()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAgentById(@PathVariable String id) {
        AbstractAgent agent = agentService.getAgentById(id);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> result = Map.<String, Object>of(
                "id", agent.getId(),
                "name", agent.getName(),
                "description", agent.getDescription(),
                "status", agent.getStatus().name()
        );
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/{id}/message")
    public ResponseEntity<AgentResult> processMessage(@PathVariable String id, @RequestBody Map<String, String> request) {
        String message = request.get("message");
        String sessionId = request.get("sessionId");
        if (message == null) {
            return ResponseEntity.badRequest().build();
        }
        AgentResult result = agentService.processMessage(id, sessionId, message);
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/{id}/execute")
    public ResponseEntity<AgentResult> executeTask(@PathVariable String id, @RequestBody AgentTask task) {
        AgentResult result = agentService.executeTask(id, task);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/codebase-maintainer/projects")
    public ResponseEntity<List<Map<String, String>>> getAvailableProjects() {
        List<Map<String, String>> projects = codebaseProperties.getProjects().stream()
                .map(p -> Map.of("name", p.getName(), "path", p.getPath()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(projects);
    }
    
    @PostMapping("/codebase-maintainer/initialize")
    public ResponseEntity<Map<String, Object>> initializeCodebaseMaintainer(@RequestBody Map<String, String> request) {
        String projectName = request.get("projectName");
        String codebasePath = request.get("codebasePath");
        if (projectName == null || codebasePath == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "projectName and codebasePath are required"));
        }
        try {
            agentService.initializeCodebaseMaintainer(projectName, codebasePath);
            return ResponseEntity.ok(Map.of("success", true, "message", "CodebaseMaintainer initialized successfully"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    @GetMapping("/codebase-maintainer/stats")
    public ResponseEntity<Map<String, Object>> getCodebaseMaintainerStats() {
        return ResponseEntity.ok(agentService.getCodebaseMaintainerStats());
    }
    
    @PostMapping("/codebase-maintainer/explore")
    public ResponseEntity<Map<String, Object>> codebaseMaintainerExplore(@RequestBody Map<String, String> request) {
        String target = request.getOrDefault("target", "");
        String result = agentService.codebaseMaintainerExplore(target);
        return ResponseEntity.ok(Map.of("result", result));
    }
    
    @PostMapping("/codebase-maintainer/analyze")
    public ResponseEntity<Map<String, Object>> codebaseMaintainerAnalyze(@RequestBody(required = false) Map<String, String> request) {
        String focus = request != null ? request.get("focus") : null;
        String result = agentService.codebaseMaintainerAnalyze(focus);
        return ResponseEntity.ok(Map.of("result", result));
    }
    
    @PostMapping("/codebase-maintainer/plan")
    public ResponseEntity<Map<String, Object>> codebaseMaintainerPlanNextSteps() {
        String result = agentService.codebaseMaintainerPlanNextSteps();
        return ResponseEntity.ok(Map.of("result", result));
    }
}
