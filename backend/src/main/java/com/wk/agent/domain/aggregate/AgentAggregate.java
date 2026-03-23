package com.wk.agent.domain.aggregate;

import com.wk.agent.domain.entity.AgentTask;
import com.wk.agent.domain.entity.Message;
import com.wk.agent.domain.valueobject.AgentStatus;
import com.wk.agent.domain.valueobject.AgentResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AgentAggregate {
    private String id;
    private String name;
    private String type;
    private AgentStatus status;
    private List<AgentTask> tasks;
    private List<Message> messages;

    public AgentAggregate(String name, String type) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.type = type;
        this.status = AgentStatus.IDLE;
        this.tasks = new ArrayList<>();
        this.messages = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public AgentStatus getStatus() {
        return status;
    }

    public List<AgentTask> getTasks() {
        return tasks;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setStatus(AgentStatus status) {
        this.status = status;
    }

    public void addTask(AgentTask task) {
        tasks.add(task);
    }

    public void addMessage(Message message) {
        messages.add(message);
    }

    public AgentResult processMessage(String content) {
        // 领域逻辑处理消息
        Message message = new Message(content, "user");
        addMessage(message);
        return new AgentResult("处理消息: " + content, true);
    }

    public AgentResult executeTask(AgentTask task) {
        // 领域逻辑执行任务
        addTask(task);
        return new AgentResult("执行任务: " + task.getTaskId(), true);
    }
}