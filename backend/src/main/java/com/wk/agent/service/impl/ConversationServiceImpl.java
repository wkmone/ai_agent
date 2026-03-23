package com.wk.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wk.agent.entity.Conversation;
import com.wk.agent.mapper.ConversationMapper;
import com.wk.agent.service.ConversationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话服务实现类
 */
@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation> implements ConversationService {

    @Override
    public Conversation getBySessionId(String sessionId) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getSessionId, sessionId)
               .eq(Conversation::getDeleted, 0);
        return baseMapper.selectOne(wrapper);
    }

    @Override
    public List<Conversation> getByUserId(String userId) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getUserId, userId)
               .eq(Conversation::getDeleted, 0)
               .orderByDesc(Conversation::getUpdateTime);
        return baseMapper.selectList(wrapper);
    }

    @Override
    public Conversation createConversation(Conversation conversation) {
        conversation.setCreateTime(LocalDateTime.now())
                   .setUpdateTime(LocalDateTime.now())
                   .setStatus(0)
                   .setDeleted(0);
        save(conversation);
        return conversation;
    }

    @Override
    public boolean updateStatus(String sessionId, Integer status) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getSessionId, sessionId);
        Conversation conversation = new Conversation();
        conversation.setStatus(status)
                   .setUpdateTime(LocalDateTime.now());
        return update(conversation, wrapper);
    }

    @Override
    public boolean deleteBySessionId(String sessionId) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getSessionId, sessionId);
        Conversation conversation = new Conversation();
        conversation.setDeleted(1)
                   .setUpdateTime(LocalDateTime.now());
        return update(conversation, wrapper);
    }
}
