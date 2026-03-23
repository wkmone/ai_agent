package com.wk.agent.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "创建或更新智能体配置请求")
public class AgentConfigRequest {

    @Schema(description = "智能体名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "名称不能为空")
    private String name;

    @Schema(description = "智能体描述")
    private String description;

    @Schema(description = "基础Agent类型", example = "SimpleAgent")
    @NotBlank(message = "Agent类型不能为空")
    private String baseAgentType;

    @Schema(description = "引用的模型配置ID", example = "1")
    private Long modelConfigId;

    @Schema(description = "模型名称（已废弃，请使用modelConfigId）")
    private String modelName;

    @Schema(description = "温度参数", example = "0.7")
    private Double temperature;

    @Schema(description = "知识库ID")
    private Long knowledgeBaseId;

    @Schema(description = "工具ID列表，逗号分隔", example = "1,2,3")
    private String tools;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBaseAgentType() {
        return baseAgentType;
    }

    public void setBaseAgentType(String baseAgentType) {
        this.baseAgentType = baseAgentType;
    }

    public Long getModelConfigId() {
        return modelConfigId;
    }

    public void setModelConfigId(Long modelConfigId) {
        this.modelConfigId = modelConfigId;
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
}