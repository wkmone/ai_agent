package com.wk.agent.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    private String message;           // 用户消息
    private String systemPrompt;     // 系统提示词（可选）
    private String model;            // 模型名称（可选）
    private Double temperature;      // 温度参数（可选）
    private Integer maxTokens;       // 最大token数（可选）
    private String sessionId;        // 会话ID（可选）
}