package com.wk.agent.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private boolean success;         // 请求是否成功
    private String message;          // 响应消息
    private String model;           // 使用的模型
    private Long tokenCount;        // token使用量
    private Long timestamp;         // 时间戳

    public static ChatResponse success(String message, String model, Long tokenCount) {
        return new ChatResponse(true, message, model, tokenCount, System.currentTimeMillis());
    }

    public static ChatResponse error(String message) {
        return new ChatResponse(false, message, null, 0L, System.currentTimeMillis());
    }
}