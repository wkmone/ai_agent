package com.wk.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wk.agent.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话Mapper接口
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
}
