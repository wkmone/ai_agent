package com.wk.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wk.agent.entity.RagChunk;
import com.wk.agent.entity.RagKnowledgeBase;
import com.wk.agent.mapper.RagChunkMapper;
import com.wk.agent.mapper.RagKnowledgeBaseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class RagKnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(RagKnowledgeBaseService.class);
    private static final String DOC_VECTOR_PREFIX = "rag:doc:vectors:";

    @Autowired
    private RagKnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private RagChunkMapper ragChunkMapper;

    @Autowired(required = false)
    private VectorStore vectorStore;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    public List<RagKnowledgeBase> getAllKnowledgeBases() {
        return knowledgeBaseMapper.selectList(
            new LambdaQueryWrapper<RagKnowledgeBase>()
                .orderByDesc(RagKnowledgeBase::getCreatedAt)
        );
    }

    public RagKnowledgeBase getKnowledgeBaseById(Long id) {
        return knowledgeBaseMapper.selectById(id);
    }

    public RagKnowledgeBase getKnowledgeBaseByNamespace(String namespace) {
        return knowledgeBaseMapper.selectOne(
            new LambdaQueryWrapper<RagKnowledgeBase>()
                .eq(RagKnowledgeBase::getNamespace, namespace)
        );
    }

    public RagKnowledgeBase createKnowledgeBase(String name, String description, String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            namespace = "kb_" + UUID.randomUUID().toString().substring(0, 8);
        }

        RagKnowledgeBase existing = knowledgeBaseMapper.selectOne(
            new LambdaQueryWrapper<RagKnowledgeBase>()
                .eq(RagKnowledgeBase::getName, name)
                .or()
                .eq(RagKnowledgeBase::getNamespace, namespace)
        );

        if (existing != null) {
            throw new RuntimeException("知识库名称或命名空间已存在");
        }

        RagKnowledgeBase kb = new RagKnowledgeBase();
        kb.setName(name);
        kb.setDescription(description);
        kb.setNamespace(namespace);
        kb.setCreatedAt(LocalDateTime.now());
        kb.setUpdatedAt(LocalDateTime.now());

        knowledgeBaseMapper.insert(kb);
        return kb;
    }

    public void updateKnowledgeBase(Long id, String name, String description) {
        RagKnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new RuntimeException("知识库不存在");
        }

        if (name != null && !name.equals(kb.getName())) {
            RagKnowledgeBase existing = knowledgeBaseMapper.selectOne(
                new LambdaQueryWrapper<RagKnowledgeBase>()
                    .eq(RagKnowledgeBase::getName, name)
                    .ne(RagKnowledgeBase::getId, id)
            );
            if (existing != null) {
                throw new RuntimeException("知识库名称已存在");
            }
            kb.setName(name);
        }

        if (description != null) {
            kb.setDescription(description);
        }

        kb.setUpdatedAt(LocalDateTime.now());
        knowledgeBaseMapper.updateById(kb);
    }

    @Transactional
    public void deleteKnowledgeBase(Long id) {
        RagKnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new RuntimeException("知识库不存在");
        }

        List<String> documentIds = ragChunkMapper.findDistinctDocumentIdsByKnowledgeBaseId(id);

        List<String> allVectorIds = new ArrayList<>();
        for (String documentId : documentIds) {
            List<String> vectorIds = getVectorIds(documentId);
            if (vectorIds != null && !vectorIds.isEmpty()) {
                allVectorIds.addAll(vectorIds);
            }
        }

        if (!allVectorIds.isEmpty() && vectorStore != null) {
            try {
                vectorStore.delete(allVectorIds);
            } catch (Exception e) {
                log.error("删除向量存储失败", e);
            }
        }

        for (String documentId : documentIds) {
            deleteVectorIds(documentId);
        }

        ragChunkMapper.deleteByKnowledgeBaseId(id);
        knowledgeBaseMapper.deleteById(id);
    }

    public Map<String, Object> getKnowledgeBaseStats(Long id) {
        RagKnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new RuntimeException("知识库不存在");
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("id", kb.getId());
        stats.put("name", kb.getName());
        stats.put("description", kb.getDescription());
        stats.put("namespace", kb.getNamespace());
        stats.put("createdAt", kb.getCreatedAt());

        Integer chunkCount = ragChunkMapper.countByKnowledgeBaseId(id);
        stats.put("chunkCount", chunkCount != null ? chunkCount : 0);

        List<String> documentIds = ragChunkMapper.findDistinctDocumentIdsByKnowledgeBaseId(id);
        stats.put("documentCount", documentIds.size());

        List<RagChunk> chunks = ragChunkMapper.findByKnowledgeBaseId(id);
        int totalTokens = chunks.stream()
            .mapToInt(c -> c.getTokenCount() != null ? c.getTokenCount() : 0)
            .sum();
        stats.put("totalTokens", totalTokens);

        return stats;
    }

    public boolean exists(Long id) {
        return knowledgeBaseMapper.selectById(id) != null;
    }

    private List<String> getVectorIds(String documentId) {
        if (redisTemplate != null) {
            String key = DOC_VECTOR_PREFIX + documentId;
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof List) {
                return (List<String>) value;
            }
        }
        return new ArrayList<>();
    }

    private void deleteVectorIds(String documentId) {
        if (redisTemplate != null) {
            String key = DOC_VECTOR_PREFIX + documentId;
            redisTemplate.delete(key);
        }
    }
}
