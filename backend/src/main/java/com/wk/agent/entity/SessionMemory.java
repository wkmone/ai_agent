package com.wk.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("session_memory")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionMemory {

    public static final int LAYER_DIALOG = 1;
    public static final int LAYER_WORKING = 2;
    public static final int LAYER_EPISODIC = 3;
    public static final int LAYER_SEMANTIC = 4;

    public static final String TYPE_WORKING = "working";
    public static final String TYPE_EPISODIC = "episodic";
    public static final String TYPE_SEMANTIC = "semantic";
    public static final String TYPE_DIALOG = "dialog";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String sessionId;

    private String userId;

    private String memoryType;

    private Integer layerLevel;

    private String content;

    private String keywords;

    private Double importance;

    private LocalDateTime createdAt;

    private LocalDateTime accessedAt;

    private Integer accessCount;

    private LocalDateTime expiresAt;

    private String metadata;

    private String summary;

    @JsonIgnore
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    @JsonIgnore
    public boolean isImportant() {
        return importance != null && importance >= 0.6;
    }
}