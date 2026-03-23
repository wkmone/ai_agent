package com.wk.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wk.agent.entity.AgentTask;

import java.util.List;

/**
 * Agent任务服务接口
 */
public interface AgentTaskService extends IService<AgentTask> {

    /**
     * 根据任务ID获取任务
     */
    AgentTask getByTaskId(String taskId);

    /**
     * 根据会话ID获取任务列表
     */
    List<AgentTask> getBySessionId(String sessionId);

    /**
     * 创建任务
     */
    AgentTask createTask(AgentTask task);

    /**
     * 更新任务状态
     */
    boolean updateStatus(String taskId, Integer status);

    /**
     * 更新任务结果
     */
    boolean updateResult(String taskId, String result);

    /**
     * 获取待处理的任务
     */
    List<AgentTask> getPendingTasks();

    /**
     * 删除任务
     */
    boolean deleteByTaskId(String taskId);
}
