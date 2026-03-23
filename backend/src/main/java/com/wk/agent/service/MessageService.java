package com.wk.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wk.agent.entity.Message;

import java.util.List;

/**
 * 消息服务接口
 */
public interface MessageService extends IService<Message> {

    /**
     * 根据会话ID获取消息列表
     */
    List<Message> getBySessionId(String sessionId);

    /**
     * 保存消息
     */
    Message saveMessage(Message message);

    /**
     * 批量保存消息
     */
    boolean saveBatchMessages(List<Message> messages);

    /**
     * 获取会话的最新消息
     */
    Message getLatestMessage(String sessionId);

    /**
     * 删除会话的所有消息
     */
    boolean deleteBySessionId(String sessionId);
}
