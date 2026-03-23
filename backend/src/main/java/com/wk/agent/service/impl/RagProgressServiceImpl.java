package com.wk.agent.service.impl;

import com.wk.agent.rabbitmq.dto.RagProcessingProgress;
import com.wk.agent.service.RagProgressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RagProgressServiceImpl implements RagProgressService {

    private final Map<String, RagProcessingProgress> progressMap = new ConcurrentHashMap<>();

    @Override
    public void saveProgress(RagProcessingProgress progress) {
        progressMap.put(progress.getTaskId(), progress);
        log.debug("保存进度: taskId={}, status={}", progress.getTaskId(), progress.getStatus());
    }

    @Override
    public RagProcessingProgress getProgress(String taskId) {
        return progressMap.get(taskId);
    }

    @Override
    public Map<String, RagProcessingProgress> getAllProgress() {
        return new ConcurrentHashMap<>(progressMap);
    }

    @Override
    public void clearProgress(String taskId) {
        progressMap.remove(taskId);
        log.debug("清除进度: taskId={}", taskId);
    }

    @Override
    public void clearAllProgress() {
        progressMap.clear();
        log.info("清除所有进度");
    }
}
