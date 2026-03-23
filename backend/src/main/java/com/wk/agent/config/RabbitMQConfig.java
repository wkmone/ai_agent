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
    public static final String RAG_PROCESSING_EXCHANGE = "rag.processing.exchange";
    public static final String RAG_PROGRESS_EXCHANGE = "rag.progress.exchange";

    // 队列名称
    public static final String CHAT_QUEUE = "chat.queue";
    public static final String TASK_QUEUE = "task.queue";
    public static final String NOTIFICATION_QUEUE = "notification.queue";
    public static final String RAG_PROCESSING_QUEUE = "rag.processing.queue";
    public static final String RAG_PROGRESS_QUEUE = "rag.progress.queue";
    public static final String RAG_DLQ = "rag.dlq";

    // 路由键
    public static final String CHAT_ROUTING_KEY = "chat.#";
    public static final String TASK_ROUTING_KEY = "task.#";
    public static final String NOTIFICATION_ROUTING_KEY = "notification.#";
    public static final String RAG_PROCESSING_ROUTING_KEY = "rag.processing.#";
    public static final String RAG_PROGRESS_ROUTING_KEY = "rag.progress.#";
    public static final String RAG_DLQ_ROUTING_KEY = "rag.dlq.#";

    /**
     * 创建 Agent 交换机
     */
    @Bean
    public Exchange agentExchange() {
        return ExchangeBuilder.topicExchange(AGENT_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 创建 RAG 处理交换机
     */
    @Bean
    public Exchange ragProcessingExchange() {
        return ExchangeBuilder.topicExchange(RAG_PROCESSING_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 创建 RAG 进度通知交换机
     */
    @Bean
    public Exchange ragProgressExchange() {
        return ExchangeBuilder.topicExchange(RAG_PROGRESS_EXCHANGE)
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
     * 创建 RAG 处理队列（带死信队列）
     */
    @Bean
    public Queue ragProcessingQueue() {
        return QueueBuilder.durable(RAG_PROCESSING_QUEUE)
                .withArgument("x-dead-letter-exchange", RAG_PROCESSING_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "rag.dlq")
                .build();
    }

    /**
     * 创建 RAG 进度通知队列
     */
    @Bean
    public Queue ragProgressQueue() {
        return QueueBuilder.durable(RAG_PROGRESS_QUEUE)
                .build();
    }

    /**
     * 创建 RAG 死信队列
     */
    @Bean
    public Queue ragDlq() {
        return QueueBuilder.durable(RAG_DLQ)
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

    /**
     * 绑定 RAG 处理队列到交换机
     */
    @Bean
    public Binding ragProcessingBinding(Queue ragProcessingQueue, Exchange ragProcessingExchange) {
        return BindingBuilder.bind(ragProcessingQueue)
                .to(ragProcessingExchange)
                .with(RAG_PROCESSING_ROUTING_KEY)
                .noargs();
    }

    /**
     * 绑定 RAG 进度通知队列到交换机
     */
    @Bean
    public Binding ragProgressBinding(Queue ragProgressQueue, Exchange ragProgressExchange) {
        return BindingBuilder.bind(ragProgressQueue)
                .to(ragProgressExchange)
                .with(RAG_PROGRESS_ROUTING_KEY)
                .noargs();
    }

    /**
     * 绑定 RAG 死信队列到交换机
     */
    @Bean
    public Binding ragDlqBinding(Queue ragDlq, Exchange ragProcessingExchange) {
        return BindingBuilder.bind(ragDlq)
                .to(ragProcessingExchange)
                .with(RAG_DLQ_ROUTING_KEY)
                .noargs();
    }
}
