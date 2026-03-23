package com.wk.agent.rabbitmq;

import com.wk.agent.service.AgentTaskService;
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
}
