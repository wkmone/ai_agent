package com.wk.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_note")
public class AgentNote {

    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String noteId;
    
    private String title;
    
    private String content;
    
    private String noteType;
    
    private String tags;
    
    private String sessionId;
    
    private String projectName;
    
    private Double importance;
    
    private Integer accessCount;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;

    public static final String TYPE_TASK_STATE = "task_state";
    public static final String TYPE_CONCLUSION = "conclusion";
    public static final String TYPE_BLOCKER = "blocker";
    public static final String TYPE_ACTION = "action";
    public static final String TYPE_REFERENCE = "reference";
    public static final String TYPE_GENERAL = "general";
}
