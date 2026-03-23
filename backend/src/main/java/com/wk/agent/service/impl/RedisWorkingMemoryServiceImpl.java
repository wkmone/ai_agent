package com.wk.agent.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wk.agent.entity.SessionMemory;
import com.wk.agent.service.RedisWorkingMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RedisWorkingMemoryServiceImpl implements RedisWorkingMemoryService {

    private static final Logger log = LoggerFactory.getLogger(RedisWorkingMemoryServiceImpl.class);

    private static final String WORKING_MEMORY_KEY_PREFIX = "memory:working:";
    private static final int WORKING_MAX_SIZE = 20;
    private static final Duration TTL = Duration.ofHours(1);

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private boolean redisAvailable = false;
    private final Map<String, LinkedList<SessionMemory>> inMemoryStore = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            if (redisTemplate != null) {
                redisTemplate.getConnectionFactory().getConnection().ping();
                redisAvailable = true;
                log.info("Redis is available, using Redis for working memory");
            } else {
                log.warn("RedisTemplate not available, using in-memory store for working memory");
            }
        } catch (Exception e) {
            log.warn("Redis not available, using in-memory store for working memory: {}", e.getMessage());
        }
    }

    @Override
    public void addWorkingMemory(String sessionId, String content, Double importance) {
        SessionMemory memory = new SessionMemory();
        memory.setSessionId(sessionId);
        memory.setMemoryType(SessionMemory.TYPE_WORKING);
        memory.setLayerLevel(SessionMemory.LAYER_WORKING);
        memory.setContent(content);
        memory.setImportance(importance != null ? importance : 0.5);
        memory.setCreatedAt(LocalDateTime.now());
        memory.setAccessedAt(LocalDateTime.now());
        memory.setAccessCount(1);

        if (redisAvailable && redisTemplate != null) {
            try {
                String key = getKey(sessionId);
                String memoryJson = objectMapper.writeValueAsString(memory);
                redisTemplate.opsForList().rightPush(key, memoryJson);
                redisTemplate.expire(key, TTL);
                enforceMaxSizeRedis(sessionId);
                log.debug("添加工作记忆到Redis: sessionId={}, importance={}", sessionId, importance);
            } catch (JsonProcessingException e) {
                log.error("序列化记忆失败: {}", e.getMessage());
            }
        } else {
            inMemoryStore.computeIfAbsent(sessionId, k -> new LinkedList<>());
            LinkedList<SessionMemory> memories = inMemoryStore.get(sessionId);
            memories.add(memory);
            enforceMaxSizeInMemory(sessionId);
            log.debug("添加工作记忆到内存: sessionId={}, importance={}", sessionId, importance);
        }
    }

    @Override
    public List<SessionMemory> getWorkingMemories(String sessionId) {
        if (redisAvailable && redisTemplate != null) {
            String key = getKey(sessionId);
            List<String> memories = redisTemplate.opsForList().range(key, 0, WORKING_MAX_SIZE - 1);

            if (memories == null || memories.isEmpty()) {
                return new ArrayList<>();
            }

            List<SessionMemory> result = new ArrayList<>();
            for (String memoryJson : memories) {
                try {
                    SessionMemory memory = objectMapper.readValue(memoryJson, SessionMemory.class);
                    memory.setAccessedAt(LocalDateTime.now());
                    memory.setAccessCount(memory.getAccessCount() != null ? memory.getAccessCount() + 1 : 1);
                    result.add(memory);
                } catch (JsonProcessingException e) {
                    log.warn("反序列化记忆失败: {}", e.getMessage());
                }
            }
            return result;
        } else {
            LinkedList<SessionMemory> memories = inMemoryStore.getOrDefault(sessionId, new LinkedList<>());
            List<SessionMemory> result = new ArrayList<>(memories);
            for (SessionMemory memory : result) {
                memory.setAccessedAt(LocalDateTime.now());
                memory.setAccessCount(memory.getAccessCount() != null ? memory.getAccessCount() + 1 : 1);
            }
            return result;
        }
    }

    @Override
    public void clearWorkingMemory(String sessionId) {
        if (redisAvailable && redisTemplate != null) {
            String key = getKey(sessionId);
            redisTemplate.delete(key);
            log.info("清空Redis工作记忆: sessionId={}", sessionId);
        } else {
            inMemoryStore.remove(sessionId);
            log.info("清空内存工作记忆: sessionId={}", sessionId);
        }
    }

    @Override
    public int getWorkingMemoryCount(String sessionId) {
        if (redisAvailable && redisTemplate != null) {
            String key = getKey(sessionId);
            Long size = redisTemplate.opsForList().size(key);
            return size != null ? size.intValue() : 0;
        } else {
            LinkedList<SessionMemory> memories = inMemoryStore.get(sessionId);
            return memories != null ? memories.size() : 0;
        }
    }

    private String getKey(String sessionId) {
        return WORKING_MEMORY_KEY_PREFIX + sessionId;
    }

    private void enforceMaxSizeRedis(String sessionId) {
        String key = getKey(sessionId);
        Long size = redisTemplate.opsForList().size(key);

        if (size != null && size > WORKING_MAX_SIZE) {
            redisTemplate.opsForList().trim(key, size - WORKING_MAX_SIZE, -1);
            log.debug("Redis工作记忆超出上限，清理旧记录: sessionId={}, deleted={}", sessionId, size - WORKING_MAX_SIZE);
        }
    }

    private void enforceMaxSizeInMemory(String sessionId) {
        LinkedList<SessionMemory> memories = inMemoryStore.get(sessionId);
        if (memories != null && memories.size() > WORKING_MAX_SIZE) {
            while (memories.size() > WORKING_MAX_SIZE) {
                memories.removeFirst();
            }
            log.debug("内存工作记忆超出上限，清理旧记录: sessionId={}", sessionId);
        }
    }
}
