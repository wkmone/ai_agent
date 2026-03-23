package com.wk.agent.service;

import com.wk.agent.entity.SessionMemory;
import java.util.List;

public interface RedisWorkingMemoryService {

    void addWorkingMemory(String sessionId, String content, Double importance);

    List<SessionMemory> getWorkingMemories(String sessionId);

    void clearWorkingMemory(String sessionId);

    int getWorkingMemoryCount(String sessionId);
}