package com.wk.agent.controller;

import com.wk.agent.dto.ChatRequest;
import com.wk.agent.dto.ChatResponse;
import com.wk.agent.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    /**
     * 简单聊天 - GET
     */
    @GetMapping("/simple")
    public ChatResponse simpleChat(@RequestParam String message) {
        return chatService.simpleChat(message);
    }

    /**
     * 简单聊天 - POST
     */
    @PostMapping("/simple")
    public ChatResponse simpleChatPost(@RequestBody ChatRequest request) {
        return chatService.chatWithParams(request);
    }

    /**
     * 带参数的聊天
     */
    @PostMapping("/advanced")
    public ChatResponse advancedChat(@RequestBody ChatRequest request) {
        return chatService.chatWithParams(request);
    }

    /**
     * 流式聊天
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestParam String message) {
        return chatService.streamChat(message);
    }

    /**
     * 带会话历史的聊天
     */
    @PostMapping("/session/{sessionId}")
    public ChatResponse chatWithSession(@PathVariable String sessionId,
                                        @RequestBody ChatRequest request) {
        return chatService.chatWithHistory(sessionId, request.getMessage());
    }

    /**
     * 清空会话记忆
     */
    @DeleteMapping("/session/{sessionId}/clear")
    public ResponseEntity<Map<String, Object>> clearSession(@PathVariable String sessionId) {
        chatService.clearSessionHistory(sessionId);
        return ResponseEntity.ok(Map.of("success", true, "message", "会话记忆已清空"));
    }
}