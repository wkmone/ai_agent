package com.wk.agent.service.impl;

import com.wk.agent.entity.SessionMemory;
import com.wk.agent.mapper.SessionMemoryMapper;
import com.wk.agent.service.MemoryImportanceCalculator;
import com.wk.agent.service.MultiLayerMemoryManager;
import com.wk.agent.service.RedisWorkingMemoryService;
import com.wk.agent.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class MultiLayerMemoryManagerImpl implements MultiLayerMemoryManager {

    private static final Logger log = LoggerFactory.getLogger(MultiLayerMemoryManagerImpl.class);

    private static final int EPISODIC_MAX_SIZE = 100;
    private static final int SEMANTIC_MAX_SIZE = 500;
    private static final double EPISODIC_MIN_IMPORTANCE = 0.6;
    private static final double SEMANTIC_MIN_IMPORTANCE = 0.7;
    private static final int EPISODIC_MAX_AGE_DAYS = 30;

    @Autowired
    private SessionMemoryMapper sessionMemoryMapper;

    @Autowired
    private RedisWorkingMemoryService redisWorkingMemoryService;

    @Autowired
    private MemoryImportanceCalculator importanceCalculator;

    @Autowired(required = false)
    private VectorSearchService vectorSearchService;

    @Override
    public void addWorkingMemory(String sessionId, String content, Double importance) {
        redisWorkingMemoryService.addWorkingMemory(sessionId, content, importance);
    }

    @Override
    @Transactional
    public void addEpisodicMemory(String sessionId, String content, Double importance, String keywords) {
        if (importance == null || importance < EPISODIC_MIN_IMPORTANCE) {
            importance = importanceCalculator.calculateImportanceScore(content);
            if (importance < EPISODIC_MIN_IMPORTANCE) {
                return;
            }
        }

        SessionMemory memory = createMemory(sessionId, SessionMemory.TYPE_EPISODIC, SessionMemory.LAYER_EPISODIC, content, importance, keywords);
        memory.setExpiresAt(LocalDateTime.now().plusDays(EPISODIC_MAX_AGE_DAYS));
        sessionMemoryMapper.insert(memory);

        enforceMaxSize(sessionId, SessionMemory.LAYER_EPISODIC, EPISODIC_MAX_SIZE);
        log.debug("添加情景记忆: sessionId={}, importance={}", sessionId, importance);
        
        indexMemoryToVectorStore(memory);
    }

    @Override
    @Transactional
    public void addSemanticMemory(String sessionId, String content, Double importance, String keywords) {
        if (importance == null || importance < SEMANTIC_MIN_IMPORTANCE) {
            importance = importanceCalculator.calculateImportanceScore(content);
            if (importance < SEMANTIC_MIN_IMPORTANCE) {
                return;
            }
        }

        SessionMemory memory = createMemory(sessionId, SessionMemory.TYPE_SEMANTIC, SessionMemory.LAYER_SEMANTIC, content, importance, keywords);
        memory.setExpiresAt(null);
        sessionMemoryMapper.insert(memory);

        enforceMaxSize(sessionId, SessionMemory.LAYER_SEMANTIC, SEMANTIC_MAX_SIZE);
        log.debug("添加语义记忆: sessionId={}, importance={}", sessionId, importance);
        
        indexMemoryToVectorStore(memory);
    }

    @Override
    public List<SessionMemory> getWorkingMemories(String sessionId) {
        return redisWorkingMemoryService.getWorkingMemories(sessionId);
    }

    @Override
    public List<SessionMemory> getEpisodicMemories(String sessionId) {
        List<SessionMemory> memories = sessionMemoryMapper.findBySessionIdAndLayerWithMinImportance(
                sessionId, SessionMemory.LAYER_EPISODIC, 0.0, EPISODIC_MAX_SIZE);
        updateMemoryAccess(memories);
        return memories;
    }

    @Override
    public List<SessionMemory> getSemanticMemories(String sessionId) {
        List<SessionMemory> memories = sessionMemoryMapper.findBySessionIdAndLayerWithMinImportance(
                sessionId, SessionMemory.LAYER_SEMANTIC, 0.0, SEMANTIC_MAX_SIZE);
        updateMemoryAccess(memories);
        return memories;
    }

    @Override
    public List<SessionMemory> getRecentMemories(String sessionId, int limit) {
        return sessionMemoryMapper.findRecentBySessionId(sessionId, limit);
    }

    @Override
    public List<SessionMemory> searchMemories(String sessionId, String query) {
        List<SessionMemory> results = new ArrayList<>();
        LocalDateTime currentTime = LocalDateTime.now();
        
        List<SessionMemory> workingMemories = redisWorkingMemoryService.getWorkingMemories(sessionId);
        for (SessionMemory memory : workingMemories) {
            if (memory.getContent() != null && memory.getContent().toLowerCase().contains(query.toLowerCase())) {
                results.add(memory);
            }
        }
        
        List<SessionMemory> dbMemories = sessionMemoryMapper.findByKeyword(sessionId, query);
        results.addAll(dbMemories);
        
        results.sort(Comparator.comparingDouble((SessionMemory m) -> 
            importanceCalculator.calculateCombinedImportance(m, currentTime, query)
        ).reversed());
        
        return results;
    }

    @Override
    public void clearWorkingMemory(String sessionId) {
        redisWorkingMemoryService.clearWorkingMemory(sessionId);
        log.info("清空工作记忆: sessionId={}", sessionId);
    }

    @Override
    @Transactional
    public void clearEpisodicMemory(String sessionId) {
        List<SessionMemory> memories = getEpisodicMemories(sessionId);
        for (SessionMemory memory : memories) {
            deleteMemoryFromVectorStore(memory);
        }
        sessionMemoryMapper.deleteBySessionIdAndLayer(sessionId, SessionMemory.LAYER_EPISODIC);
        log.info("清空情景记忆: sessionId={}", sessionId);
    }

    @Override
    @Transactional
    public void clearSemanticMemory(String sessionId) {
        List<SessionMemory> memories = getSemanticMemories(sessionId);
        for (SessionMemory memory : memories) {
            deleteMemoryFromVectorStore(memory);
        }
        sessionMemoryMapper.deleteBySessionIdAndLayer(sessionId, SessionMemory.LAYER_SEMANTIC);
        log.info("清空语义记忆: sessionId={}", sessionId);
    }

    @Override
    @Transactional
    public void clearAllMemory(String sessionId) {
        List<SessionMemory> episodicMemories = getEpisodicMemories(sessionId);
        for (SessionMemory memory : episodicMemories) {
            deleteMemoryFromVectorStore(memory);
        }
        List<SessionMemory> semanticMemories = getSemanticMemories(sessionId);
        for (SessionMemory memory : semanticMemories) {
            deleteMemoryFromVectorStore(memory);
        }
        redisWorkingMemoryService.clearWorkingMemory(sessionId);
        sessionMemoryMapper.deleteBySessionId(sessionId);
        log.info("清空所有记忆: sessionId={}", sessionId);
    }

    @Override
    public String buildContextPrompt(String sessionId, String userQuery) {
        log.debug("构建记忆上下文: sessionId={}, query={}", sessionId, userQuery);

        if (sessionId == null || sessionId.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();

        List<SessionMemory> semanticMemories = getSemanticMemories(sessionId);
        if (!semanticMemories.isEmpty()) {
            context.append("【知识】\n");
            int index = 1;
            for (SessionMemory memory : semanticMemories) {
                if (memory.getImportance() != null && memory.getImportance() >= 0.7) {
                    context.append(index++).append(". ").append(memory.getContent());
                    if (memory.getKeywords() != null && !memory.getKeywords().isEmpty()) {
                        context.append(" [").append(memory.getKeywords()).append("]");
                    }
                    context.append("\n");
                    if (index > 5) break;
                }
            }
            context.append("\n");
        }

        List<SessionMemory> episodicMemories = getEpisodicMemories(sessionId);
        if (!episodicMemories.isEmpty()) {
            context.append("【相关事件】\n");
            int index = 1;
            for (SessionMemory memory : episodicMemories) {
                if (memory.getImportance() != null && memory.getImportance() >= 0.6) {
                    context.append(index++).append(". ").append(memory.getContent());
                    if (memory.getCreatedAt() != null) {
                        context.append(" [").append(memory.getCreatedAt().toLocalDate()).append("]");
                    }
                    context.append("\n");
                    if (index > 5) break;
                }
            }
            context.append("\n");
        }

        List<SessionMemory> workingMemories = getWorkingMemories(sessionId);
        if (!workingMemories.isEmpty()) {
            context.append("【当前上下文】\n");
            for (SessionMemory memory : workingMemories) {
                context.append("• ").append(memory.getContent()).append("\n");
            }
            context.append("\n");
        }

        if (context.length() > 0) {
            context.insert(0, "请参考以下记忆上下文回答用户问题：\n\n");
        }

        return context.toString();
    }

    private void updateMemoryAccess(List<SessionMemory> memories) {
        for (SessionMemory memory : memories) {
            if (memory.getId() != null) {
                try {
                    sessionMemoryMapper.incrementAccessCount(memory.getId());
                } catch (Exception e) {
                    log.warn("更新记忆访问时间失败: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    @Transactional
    public int cleanupExpiredMemories() {
        LocalDateTime now = LocalDateTime.now();
        List<SessionMemory> expiredMemories = sessionMemoryMapper.findExpiredMemories(now);
        
        for (SessionMemory memory : expiredMemories) {
            deleteMemoryFromVectorStore(memory);
        }
        
        int deleted = sessionMemoryMapper.deleteExpiredMemories(now);
        if (deleted > 0) {
            log.info("清理过期记忆: {} 条", deleted);
        }
        return deleted;
    }

    @Override
    public int getWorkingMemoryCount(String sessionId) {
        return redisWorkingMemoryService.getWorkingMemoryCount(sessionId);
    }

    @Override
    public int getEpisodicMemoryCount(String sessionId) {
        return sessionMemoryMapper.countBySessionIdAndLayer(sessionId, SessionMemory.LAYER_EPISODIC);
    }

    @Override
    public int getSemanticMemoryCount(String sessionId) {
        return sessionMemoryMapper.countBySessionIdAndLayer(sessionId, SessionMemory.LAYER_SEMANTIC);
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledCleanup() {
        cleanupExpiredMemories();
    }

    private SessionMemory createMemory(String sessionId, String type, int layerLevel, String content, Double importance, String keywords) {
        SessionMemory memory = new SessionMemory();
        memory.setSessionId(sessionId);
        memory.setMemoryType(type);
        memory.setLayerLevel(layerLevel);
        memory.setContent(content);
        memory.setImportance(importance);
        memory.setKeywords(keywords);
        memory.setCreatedAt(LocalDateTime.now());
        memory.setAccessedAt(LocalDateTime.now());
        memory.setAccessCount(0);
        return memory;
    }

    private void enforceMaxSize(String sessionId, int layerLevel, int maxSize) {
        int currentCount = sessionMemoryMapper.countBySessionIdAndLayer(sessionId, layerLevel);
        if (currentCount > maxSize) {
            int deleteCount = currentCount - maxSize;
            List<SessionMemory> allMemories = sessionMemoryMapper.findBySessionIdAndLayerWithMinImportance(
                    sessionId, layerLevel, 0.0, currentCount);
            
            allMemories.sort((a, b) -> {
                double scoreA = a.getImportance() != null ? a.getImportance() : 0.5;
                double scoreB = b.getImportance() != null ? b.getImportance() : 0.5;
                if (scoreA != scoreB) {
                    return Double.compare(scoreA, scoreB);
                }
                if (a.getCreatedAt() == null) return -1;
                if (b.getCreatedAt() == null) return 1;
                return a.getCreatedAt().compareTo(b.getCreatedAt());
            });
            
            for (int i = 0; i < deleteCount && i < allMemories.size(); i++) {
                SessionMemory memoryToDelete = allMemories.get(i);
                deleteMemoryFromVectorStore(memoryToDelete);
                sessionMemoryMapper.deleteById(memoryToDelete.getId());
            }
            log.debug("记忆超出上限，清理低重要度记录: layer={}, deleted={}", layerLevel, deleteCount);
        }
    }

    private void indexMemoryToVectorStore(SessionMemory memory) {
        if (vectorSearchService == null || memory.getId() == null || memory.getContent() == null) {
            return;
        }
        try {
            double[] embedding = vectorSearchService.generateEmbedding(memory.getContent());
            vectorSearchService.indexMemory(
                String.valueOf(memory.getId()),
                memory.getMemoryType(),
                memory.getSessionId(),
                memory.getContent(),
                embedding
            );
            log.debug("记忆已索引到向量库: id={}, type={}, sessionId={}", memory.getId(), memory.getMemoryType(), memory.getSessionId());
        } catch (Exception e) {
            log.warn("索引记忆到向量库失败: {}", e.getMessage());
        }
    }

    private void deleteMemoryFromVectorStore(SessionMemory memory) {
        if (vectorSearchService == null || memory.getId() == null) {
            return;
        }
        try {
            vectorSearchService.deleteMemory(String.valueOf(memory.getId()));
            log.debug("记忆已从向量库删除: id={}", memory.getId());
        } catch (Exception e) {
            log.warn("从向量库删除记忆失败: {}", e.getMessage());
        }
    }
}
