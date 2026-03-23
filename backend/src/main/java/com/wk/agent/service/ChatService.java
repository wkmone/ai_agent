package com.wk.agent.service;

import com.wk.agent.dto.ChatRequest;
import com.wk.agent.dto.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * 聊天服务接口
 */
public interface ChatService {

    /**
     * 简单聊天
     */
    ChatResponse simpleChat(String message);

    /**
     * 带参数的聊天
     */
    ChatResponse chatWithParams(ChatRequest request);

    /**
     * 流式聊天
     */
    Flux<String> streamChat(String message);

    /**
     * 聊天历史
     */
    ChatResponse chatWithHistory(String sessionId, String message);

    /**
     * 清空会话历史和记忆
     */
    void clearSessionHistory(String sessionId);
}