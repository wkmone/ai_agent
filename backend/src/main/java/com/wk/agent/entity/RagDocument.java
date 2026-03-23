package com.wk.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rag_document")
public class RagDocument {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String documentId;
    
    private String fileName;
    
    private String fileType;
    
    private String filePath;
    
    private Long fileSize;
    
    private String title;
    
    private String author;
    
    private String namespace;
    
    private Integer chunkCount;
    
    private Integer totalTokens;
    
    private String metadata;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    private Long knowledgeBaseId;
    
    private String sessionId;
}
