package com.wk.agent.service;

import com.wk.agent.rabbitmq.dto.RagProcessingProgress;

import java.util.Map;

public interface RagProgressService {

    void saveProgress(RagProcessingProgress progress);

    RagProcessingProgress getProgress(String taskId);

    Map<String, RagProcessingProgress> getAllProgress();

    void clearProgress(String taskId);

    void clearAllProgress();
}
