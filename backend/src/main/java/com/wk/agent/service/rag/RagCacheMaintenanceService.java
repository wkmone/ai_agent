package com.wk.agent.service.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class RagCacheMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(RagCacheMaintenanceService.class);

    private static final String CACHE_PREFIX = "rag:search:";
    private static final String DOC_VECTOR_PREFIX = "rag:doc:vectors:";

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.MINUTES)
    public void cleanupExpiredCaches() {
        if (redisTemplate == null) {
            return;
        }

        try {
            Set<String> keys = redisTemplate.keys(CACHE_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                int cleaned = 0;
                for (String key : keys) {
                    Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                    if (ttl != null && ttl < 0) {
                        redisTemplate.delete(key);
                        cleaned++;
                    }
                }
                if (cleaned > 0) {
                    log.info("清理过期缓存: {} 个", cleaned);
                }
            }
        } catch (Exception e) {
            log.warn("清理缓存失败: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    public void cleanupOrphanedVectorIds() {
        if (redisTemplate == null) {
            return;
        }

        try {
            Set<String> keys = redisTemplate.keys(DOC_VECTOR_PREFIX + "*");
            if (keys != null) {
                log.info("当前文档向量映射数: {}", keys.size());
            }
        } catch (Exception e) {
            log.warn("清理孤立向量ID失败: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRate = 6, timeUnit = TimeUnit.HOURS)
    public void logCacheStats() {
        if (redisTemplate == null) {
            return;
        }

        try {
            Set<String> cacheKeys = redisTemplate.keys(CACHE_PREFIX + "*");
            Set<String> docKeys = redisTemplate.keys(DOC_VECTOR_PREFIX + "*");

            int cacheCount = cacheKeys != null ? cacheKeys.size() : 0;
            int docCount = docKeys != null ? docKeys.size() : 0;

            log.info("RAG缓存统计 - 检索缓存: {} 条, 文档映射: {} 个", cacheCount, docCount);
        } catch (Exception e) {
            log.warn("获取缓存统计失败: {}", e.getMessage());
        }
    }

    public void clearAllCaches() {
        if (redisTemplate == null) {
            return;
        }

        try {
            Set<String> keys = redisTemplate.keys(CACHE_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("已清空所有检索缓存: {} 个", keys.size());
            }
        } catch (Exception e) {
            log.warn("清空缓存失败: {}", e.getMessage());
        }
    }
}
