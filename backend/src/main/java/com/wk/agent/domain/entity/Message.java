package com.wk.agent.domain.entity;

import java.time.LocalDateTime;

public class Message {
    private String messageId;
    private String content;
    private String sender;
    private LocalDateTime timestamp;

    public Message(String content, String sender) {
        this.messageId = java.util.UUID.randomUUID().toString();
        this.content = content;
        this.sender = sender;
        this.timestamp = LocalDateTime.now();
    }

    public String getMessageId() {
        return messageId;
    }

    public String getContent() {
        return content;
    }

    public String getSender() {
        return sender;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}