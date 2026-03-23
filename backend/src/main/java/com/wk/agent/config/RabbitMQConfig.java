package com.wk.agent.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置类
 */
@Configuration
public class RabbitMQConfig {

    // 交换机名称
    public static final String AGENT_EXCHANGE = "agent.exchange";

    // 队列名称
    public static final String CHAT_QUEUE = "chat.queue";
    public static final String TASK_QUEUE = "task.queue";
    public static final String NOTIFICATION_QUEUE = "notification.queue";

    // 路由键
    public static final String CHAT_ROUTING_KEY = "chat.#";
    public static final String TASK_ROUTING_KEY = "task.#";
    public static final String NOTIFICATION_ROUTING_KEY = "notification.#";

    /**
     * 创建交换机
     */
    @Bean
    public Exchange agentExchange() {
        return ExchangeBuilder.topicExchange(AGENT_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 创建聊天队列
     */
    @Bean
    public Queue chatQueue() {
        return QueueBuilder.durable(CHAT_QUEUE)
                .build();
    }

    /**
     * 创建任务队列
     */
    @Bean
    public Queue taskQueue() {
        return QueueBuilder.durable(TASK_QUEUE)
                .build();
    }

    /**
     * 创建通知队列
     */
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .build();
    }

    /**
     * 绑定聊天队列到交换机
     */
    @Bean
    public Binding chatBinding(Queue chatQueue, Exchange agentExchange) {
        return BindingBuilder.bind(chatQueue)
                .to(agentExchange)
                .with(CHAT_ROUTING_KEY)
                .noargs();
    }

    /**
     * 绑定任务队列到交换机
     */
    @Bean
    public Binding taskBinding(Queue taskQueue, Exchange agentExchange) {
        return BindingBuilder.bind(taskQueue)
                .to(agentExchange)
                .with(TASK_ROUTING_KEY)
                .noargs();
    }

    /**
     * 绑定通知队列到交换机
     */
    @Bean
    public Binding notificationBinding(Queue notificationQueue, Exchange agentExchange) {
        return BindingBuilder.bind(notificationQueue)
                .to(agentExchange)
                .with(NOTIFICATION_ROUTING_KEY)
                .noargs();
    }
}
