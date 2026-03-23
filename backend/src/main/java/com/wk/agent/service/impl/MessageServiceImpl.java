package com.wk.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wk.agent.entity.Message;
import com.wk.agent.mapper.MessageMapper;
import com.wk.agent.service.MessageService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息服务实现类
 */
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    @Override
    public List<Message> getBySessionId(String sessionId) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getSessionId, sessionId)
               .eq(Message::getDeleted, 0)
               .orderByAsc(Message::getCreateTime);
        return baseMapper.selectList(wrapper);
    }

    @Override
    public Message saveMessage(Message message) {
        message.setCreateTime(LocalDateTime.now())
               .setStatus(0)
               .setDeleted(0);
        save(message);
        return message;
    }

    @Override
    public boolean saveBatchMessages(List<Message> messages) {
        messages.forEach(message -> {
            message.setCreateTime(LocalDateTime.now())
                   .setStatus(0)
                   .setDeleted(0);
        });
        return saveBatch(messages);
    }

    @Override
    public Message getLatestMessage(String sessionId) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getSessionId, sessionId)
               .eq(Message::getDeleted, 0)
               .orderByDesc(Message::getCreateTime)
               .last("LIMIT 1");
        return baseMapper.selectOne(wrapper);
    }

    @Override
    public boolean deleteBySessionId(String sessionId) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getSessionId, sessionId);
        Message message = new Message();
        message.setDeleted(1);
        return update(message, wrapper);
    }
}
