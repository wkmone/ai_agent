package com.wk.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wk.agent.entity.AgentTask;
import com.wk.agent.mapper.AgentTaskMapper;
import com.wk.agent.service.AgentTaskService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent任务服务实现类
 */
@Service
public class AgentTaskServiceImpl extends ServiceImpl<AgentTaskMapper, AgentTask> implements AgentTaskService {

    @Override
    public AgentTask getByTaskId(String taskId) {
        LambdaQueryWrapper<AgentTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTask::getTaskId, taskId)
               .eq(AgentTask::getDeleted, 0);
        return baseMapper.selectOne(wrapper);
    }

    @Override
    public List<AgentTask> getBySessionId(String sessionId) {
        LambdaQueryWrapper<AgentTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTask::getSessionId, sessionId)
               .eq(AgentTask::getDeleted, 0)
               .orderByDesc(AgentTask::getCreateTime);
        return baseMapper.selectList(wrapper);
    }

    @Override
    public AgentTask createTask(AgentTask task) {
        task.setCreateTime(LocalDateTime.now())
            .setUpdateTime(LocalDateTime.now())
            .setStatus(0)
            .setDeleted(0);
        save(task);
        return task;
    }

    @Override
    public boolean updateStatus(String taskId, Integer status) {
        LambdaQueryWrapper<AgentTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTask::getTaskId, taskId);
        AgentTask task = new AgentTask();
        task.setStatus(status)
            .setUpdateTime(LocalDateTime.now());
        if (status == 1) {
            task.setStartTime(LocalDateTime.now());
        } else if (status == 2 || status == 3) {
            task.setEndTime(LocalDateTime.now());
        }
        return update(task, wrapper);
    }

    @Override
    public boolean updateResult(String taskId, String result) {
        LambdaQueryWrapper<AgentTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTask::getTaskId, taskId);
        AgentTask task = new AgentTask();
        task.setResult(result)
            .setUpdateTime(LocalDateTime.now())
            .setStatus(2);
        return update(task, wrapper);
    }

    @Override
    public List<AgentTask> getPendingTasks() {
        LambdaQueryWrapper<AgentTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTask::getStatus, 0)
               .eq(AgentTask::getDeleted, 0)
               .orderByAsc(AgentTask::getCreateTime);
        return baseMapper.selectList(wrapper);
    }

    @Override
    public boolean deleteByTaskId(String taskId) {
        LambdaQueryWrapper<AgentTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTask::getTaskId, taskId);
        AgentTask task = new AgentTask();
        task.setDeleted(1)
            .setUpdateTime(LocalDateTime.now());
        return update(task, wrapper);
    }
}
