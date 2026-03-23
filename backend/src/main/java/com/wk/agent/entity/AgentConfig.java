package com.wk.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_config")
public class AgentConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String baseAgentType;

    private Long modelConfigId;

    private Double temperature;

    private Long knowledgeBaseId;

    private String tools;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}