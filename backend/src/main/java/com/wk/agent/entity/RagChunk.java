package com.wk.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rag_chunk")
public class RagChunk {

    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String documentId;
    
    private String content;
    
    private Integer chunkIndex;
    
    private String headingPath;
    
    private Integer startOffset;
    
    private Integer endOffset;
    
    private Integer tokenCount;
    
    private String ragNamespace;
    
    private String sourceType;
    
    private String sourcePath;
    
    private Double importance;
    
    private Integer accessCount;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    private Long knowledgeBaseId;
    
    @TableField(exist = false)
    private float[] embedding;
}
