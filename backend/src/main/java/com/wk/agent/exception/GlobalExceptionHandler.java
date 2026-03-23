package com.wk.agent.exception;

import com.wk.agent.dto.ChatResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ChatResponse handleException(Exception e) {
        return ChatResponse.error("系统错误: " + e.getMessage());
    }
}
