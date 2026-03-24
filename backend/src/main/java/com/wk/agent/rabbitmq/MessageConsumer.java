package com.wk.agent.rabbitmq;

import com.wk.agent.rabbitmq.dto.RagProcessingProgress;
import com.wk.agent.rabbitmq.dto.RagProcessingTask;
import com.wk.agent.service.AgentTaskService;
import com.wk.agent.service.RagProgressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ消息消费者
 */
@Slf4j
@Component
public class MessageConsumer {

    @Autowired
    private AgentTaskService agentTaskService;

    @Autowired
    private MessageProducer messageProducer;

    @Autowired
    private RagAsyncProcessor ragAsyncProcessor;

    @Autowired
    private RagProgressService progressService;

    /**
     * 监听聊天队列
     */
    @RabbitListener(queues = "chat.queue")
    public void handleChatMessage(String message) {
        try {
            log.info("接收到聊天消息: {}", message);
            // 处理聊天消息逻辑
        } catch (Exception e) {
            log.error("处理聊天消息失败: {}", e.getMessage());
        }
    }

    /**
     * 监听任务队列
     */
    @RabbitListener(queues = "task.queue")
    public void handleTaskMessage(String message) {
        try {
            log.info("接收到任务消息: {}", message);
            // 处理任务消息逻辑
            String[] parts = message.split(":", 2);
            if (parts.length == 2) {
                String taskId = parts[0];
                String content = parts[1];
                // 更新任务状态为处理中
                agentTaskService.updateStatus(taskId, 1);
                // 这里可以添加具体的任务处理逻辑
                // 处理完成后更新任务状态和结果
                agentTaskService.updateResult(taskId, "任务处理完成");
            }
        } catch (Exception e) {
            log.error("处理任务消息失败: {}", e.getMessage());
        }
    }

    /**
     * 监听通知队列
     */
    @RabbitListener(queues = "notification.queue")
    public void handleNotificationMessage(String message) {
        try {
            log.info("接收到通知消息: {}", message);
            // 处理通知消息逻辑
        } catch (Exception e) {
            log.error("处理通知消息失败: {}", e.getMessage());
        }
    }

    /**
     * 监听 RAG 处理队列
     */
    @RabbitListener(queues = "rag.processing.queue")
    public void handleRagProcessingTask(RagProcessingTask task) {
        try {
            log.info("接收到 RAG 处理任务: taskId={}", task.getTaskId());
            
            // 发送开始处理的进度通知
            sendProgress(task.getTaskId(), RagProcessingProgress.Status.PARSING, 
                "开始解析文档: " + task.getFileName(), 0.1);
            
            // 调用异步处理器处理任务
            ragAsyncProcessor.processRagTask(task);
            
        } catch (Exception e) {
            log.error("处理 RAG 任务失败: taskId={}", task.getTaskId(), e);
            sendProgress(task.getTaskId(), RagProcessingProgress.Status.FAILED, 
                "处理失败: " + e.getMessage(), 0.0, null, null, e.getMessage());
        }
    }



    /**
     * 监听死信队列，记录失败的任务
     */
    @RabbitListener(queues = "rag.dlq")
    public void handleRagDlq(Object message) {
        try {
            log.warn("接收到 RAG 死信队列消息: {}", message);
            // 可以在这里记录失败任务，或者发送告警
        } catch (Exception e) {
            log.error("处理 RAG 死信队列消息失败", e);
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
}
