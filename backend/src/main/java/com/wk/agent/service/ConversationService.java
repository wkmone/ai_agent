package com.wk.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wk.agent.entity.Conversation;

import java.util.List;

/**
 * 会话服务接口
 */
public interface ConversationService extends IService<Conversation> {

    /**
     * 根据会话ID获取会话
     */
    Conversation getBySessionId(String sessionId);

    /**
     * 获取用户的所有会话
     */
    List<Conversation> getByUserId(String userId);

    /**
     * 创建新会话
     */
    Conversation createConversation(Conversation conversation);

    /**
     * 更新会话状态
     */
    boolean updateStatus(String sessionId, Integer status);

    /**
     * 删除会话
     */
    boolean deleteBySessionId(String sessionId);
}
