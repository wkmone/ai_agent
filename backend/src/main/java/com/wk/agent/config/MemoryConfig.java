package com.wk.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MemoryConfig {

    private static final Logger log = LoggerFactory.getLogger(MemoryConfig.class);

    @Autowired(required = false)
    private RedisChatMemoryRepository redisChatMemoryRepository;

    @Bean
    @Primary
    public ChatMemory chatMemory() {
        if (redisChatMemoryRepository != null) {
            log.info("Using RedisChatMemoryRepository for ChatMemory");
            return MessageWindowChatMemory.builder()
                    .chatMemoryRepository(redisChatMemoryRepository)
                    .maxMessages(50)
                    .build();
        }
        log.warn("RedisChatMemoryRepository not available, using in-memory ChatMemory");
        return MessageWindowChatMemory.builder()
                .maxMessages(50)
                .build();
    }
}