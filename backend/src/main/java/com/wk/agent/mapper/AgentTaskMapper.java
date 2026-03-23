package com.wk.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wk.agent.entity.AgentTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent任务Mapper接口
 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTask> {
}
