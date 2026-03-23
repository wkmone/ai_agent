package com.wk.agent.rabbitmq;

import com.wk.agent.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ消息生产者
 */
@Slf4j
@Component
public class MessageProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送聊天消息
     */
    public void sendChatMessage(String message) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.AGENT_EXCHANGE, "chat.message", message);
            log.info("发送聊天消息成功: {}", message);
        } catch (Exception e) {
            log.error("发送聊天消息失败: {}", e.getMessage());
        }
    }

    /**
     * 发送任务消息
     */
    public void sendTaskMessage(String taskId, String content) {
        try {
            String message = taskId + ":" + content;
            rabbitTemplate.convertAndSend(RabbitMQConfig.AGENT_EXCHANGE, "task.create", message);
            log.info("发送任务消息成功: {}", message);
        } catch (Exception e) {
            log.error("发送任务消息失败: {}", e.getMessage());
        }
    }

    /**
     * 发送通知消息
     */
    public void sendNotificationMessage(String message) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.AGENT_EXCHANGE, "notification.info", message);
            log.info("发送通知消息成功: {}", message);
        } catch (Exception e) {
            log.error("发送通知消息失败: {}", e.getMessage());
        }
    }
}
