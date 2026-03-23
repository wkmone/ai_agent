package com.wk.agent.service;

import com.wk.agent.entity.SessionMemory;
import java.util.List;

public interface MultiLayerMemoryManager {

    void addWorkingMemory(String sessionId, String content, Double importance);

    void addEpisodicMemory(String sessionId, String content, Double importance, String keywords);

    void addSemanticMemory(String sessionId, String content, Double importance, String keywords);

    List<SessionMemory> getWorkingMemories(String sessionId);

    List<SessionMemory> getEpisodicMemories(String sessionId);

    List<SessionMemory> getSemanticMemories(String sessionId);

    List<SessionMemory> getRecentMemories(String sessionId, int limit);

    List<SessionMemory> searchMemories(String sessionId, String query);

    void clearWorkingMemory(String sessionId);

    void clearEpisodicMemory(String sessionId);

    void clearSemanticMemory(String sessionId);

    void clearAllMemory(String sessionId);

    String buildContextPrompt(String sessionId, String userQuery);

    int cleanupExpiredMemories();

    int getWorkingMemoryCount(String sessionId);

    int getEpisodicMemoryCount(String sessionId);

    int getSemanticMemoryCount(String sessionId);
}