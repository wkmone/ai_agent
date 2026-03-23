package com.wk.agent.controller;

import com.wk.agent.controller.dto.AgentConfigRequest;
import com.wk.agent.entity.AgentConfig;
import com.wk.agent.service.AgentConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/agent-configs")
@Tag(name = "智能体配置管理", description = "智能体配置管理接口")
public class AgentConfigController {

    @Autowired
    private AgentConfigService agentConfigService;

    @GetMapping
    @Operation(summary = "获取所有智能体配置", description = "获取所有智能体配置列表")
    public ResponseEntity<List<AgentConfig>> getAllAgentConfigs() {
        return ResponseEntity.ok(agentConfigService.getAllAgentConfigs());
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取智能体配置详情", description = "根据ID获取智能体配置详情")
    public ResponseEntity<AgentConfig> getAgentConfigById(
            @Parameter(description = "智能体配置ID") @PathVariable Long id) {
        AgentConfig config = agentConfigService.getAgentConfigById(id);
        if (config == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(config);
    }

    @PostMapping
    @Operation(summary = "创建智能体配置", description = "创建新的智能体配置")
    public ResponseEntity<Map<String, Object>> createAgentConfig(@RequestBody AgentConfigRequest request) {
        try {
            AgentConfig agentConfig = convertRequestToEntity(request);
            AgentConfig config = agentConfigService.createAgentConfig(agentConfig);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "agentConfig", config
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新智能体配置", description = "更新智能体配置信息")
    public ResponseEntity<Map<String, Object>> updateAgentConfig(
            @Parameter(description = "智能体配置ID") @PathVariable Long id,
            @RequestBody AgentConfigRequest request) {
        try {
            AgentConfig agentConfig = convertRequestToEntity(request);
            agentConfig.setId(id);
            AgentConfig config = agentConfigService.updateAgentConfig(agentConfig);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "agentConfig", config
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除智能体配置", description = "删除智能体配置")
    public ResponseEntity<Map<String, Object>> deleteAgentConfig(
            @Parameter(description = "智能体配置ID") @PathVariable Long id) {
        try {
            boolean deleted = agentConfigService.deleteAgentConfig(id);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "deleted", deleted
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    private AgentConfig convertRequestToEntity(AgentConfigRequest request) {
        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setName(request.getName());
        agentConfig.setDescription(request.getDescription());
        agentConfig.setBaseAgentType(request.getBaseAgentType());
        agentConfig.setModelConfigId(request.getModelConfigId());
        agentConfig.setTemperature(request.getTemperature());
        agentConfig.setKnowledgeBaseId(request.getKnowledgeBaseId());
        agentConfig.setTools(request.getTools());
        return agentConfig;
    }
}