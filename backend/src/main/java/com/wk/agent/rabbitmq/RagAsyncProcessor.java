package com.wk.agent.rabbitmq;

import com.wk.agent.entity.RagChunk;
import com.wk.agent.mapper.RagChunkMapper;
import com.wk.agent.rabbitmq.dto.RagProcessingProgress;
import com.wk.agent.rabbitmq.dto.RagProcessingTask;
import com.wk.agent.service.RagKnowledgeBaseService;
import com.wk.agent.service.RagProgressService;
import com.wk.agent.service.rag.DocumentChunkingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RagAsyncProcessor {

    private static final String DOC_VECTOR_PREFIX = "rag:doc:vectors:";

    @Autowired
    private MessageProducer messageProducer;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private DocumentChunkingService chunkingService;

    @Autowired
    private RagChunkMapper ragChunkMapper;

    @Autowired
    private RagProgressService progressService;

    @Autowired(required = false)
    private RagKnowledgeBaseService knowledgeBaseService;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    public void processRagTask(RagProcessingTask task) {
        try {
            String taskId = task.getTaskId();
            log.info("开始处理 RAG 任务: taskId={}, file={}", taskId, task.getFileName());

            String documentId = task.getDocumentId();
            if (documentId == null || documentId.isEmpty()) {
                documentId = UUID.randomUUID().toString();
            }

            sendProgress(taskId, RagProcessingProgress.Status.PARSING, "正在解析文档...", 0.15);

            String content = task.getFileContent();
            if (content == null || content.isEmpty()) {
                throw new IllegalStateException("文档内容为空");
            }

            sendProgress(taskId, RagProcessingProgress.Status.CHUNKING, "正在分块处理...", 0.3);

            List<DocumentChunkingService.ChunkResult> chunks = chunkingService.chunkText(
                content, task.getChunkSize(), task.getOverlapSize());

            if (chunks.isEmpty()) {
                throw new IllegalStateException("文档分块结果为空");
            }

            log.info("文档分块完成: taskId={}, chunkCount={}", taskId, chunks.size());
            sendProgress(taskId, RagProcessingProgress.Status.VECTORIZING, "正在向量化...", 0.5);

            List<Document> documents = new ArrayList<>();
            List<RagChunk> ragChunks = new ArrayList<>();
            List<String> vectorIds = new ArrayList<>();

            for (DocumentChunkingService.ChunkResult chunk : chunks) {
                String vectorId = UUID.randomUUID().toString();
                vectorIds.add(vectorId);

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("documentId", documentId);
                metadata.put("chunkIndex", chunk.chunkIndex);
                metadata.put("headingPath", chunk.headingPath != null ? chunk.headingPath : "");
                metadata.put("ragNamespace", task.getRagNamespace() != null ? task.getRagNamespace() : "default");
                metadata.put("sourcePath", task.getSourcePath() != null ? task.getSourcePath() : "uploaded");

                Document doc = new Document(vectorId, chunk.content, metadata);
                documents.add(doc);

                RagChunk ragChunk = new RagChunk();
                ragChunk.setDocumentId(documentId);
                ragChunk.setContent(chunk.content);
                ragChunk.setChunkIndex(chunk.chunkIndex);
                ragChunk.setHeadingPath(chunk.headingPath != null ? chunk.headingPath : "");
                ragChunk.setStartOffset(chunk.startOffset);
                ragChunk.setEndOffset(chunk.endOffset);
                ragChunk.setTokenCount(chunk.tokenCount);
                ragChunk.setRagNamespace(task.getRagNamespace() != null ? task.getRagNamespace() : "default");
                ragChunk.setSourcePath(task.getSourcePath() != null ? task.getSourcePath() : "uploaded");
                ragChunk.setCreatedAt(LocalDateTime.now());
                ragChunk.setUpdatedAt(LocalDateTime.now());

                if (task.getKnowledgeBaseId() != null) {
                    ragChunk.setKnowledgeBaseId(task.getKnowledgeBaseId());
                }

                ragChunks.add(ragChunk);
            }

            sendProgress(taskId, RagProcessingProgress.Status.SAVING, "正在保存到向量数据库...", 0.75);

            vectorStore.add(documents);
            saveVectorIds(documentId, vectorIds);

            for (RagChunk rc : ragChunks) {
                ragChunkMapper.insert(rc);
            }

            int totalTokens = chunks.stream().mapToInt(c -> c.tokenCount).sum();

            log.info("RAG 任务处理完成: taskId={}, documentId={}, chunkCount={}, totalTokens={}",
                taskId, documentId, chunks.size(), totalTokens);

            sendProgress(taskId, RagProcessingProgress.Status.COMPLETED,
                "处理完成", 1.0, documentId, chunks.size(), null);

        } catch (Exception e) {
            log.error("RAG 任务处理失败: taskId={}", task.getTaskId(), e);
            sendProgress(task.getTaskId(), RagProcessingProgress.Status.FAILED,
                "处理失败: " + e.getMessage(), 0.0, null, null, e.getMessage());
        }
    }

    private void sendProgress(String taskId, RagProcessingProgress.Status status, String message, double progress) {
        sendProgress(taskId, status, message, progress, null, null, null);
    }

    private void sendProgress(String taskId, RagProcessingProgress.Status status, String message,
            double progress, String documentId, Integer chunkCount, String errorMessage) {
        RagProcessingProgress progressObj = RagProcessingProgress.builder()
            .taskId(taskId)
            .status(status)
            .message(message)
            .progress(progress)
            .documentId(documentId)
            .chunkCount(chunkCount)
            .errorMessage(errorMessage)
            .timestamp(System.currentTimeMillis())
            .build();
        // 直接更新进度，绕过RabbitMQ
        progressService.saveProgress(progressObj);
        log.debug("进度已更新: taskId={}, status={}, progress={}", taskId, status, progress);
    }

    private void saveVectorIds(String documentId, List<String> vectorIds) {
        if (redisTemplate != null) {
            String key = DOC_VECTOR_PREFIX + documentId;
            redisTemplate.opsForValue().set(key, vectorIds, 24, TimeUnit.HOURS);
        }
    }
}
