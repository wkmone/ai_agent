package com.wk.agent.rabbitmq;

import com.wk.agent.rabbitmq.dto.RagProcessingProgress;
import com.wk.agent.service.RagProgressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RagProgressListener {

    @Autowired
    private RagProgressService progressService;

    @RabbitListener(queues = "rag.progress.queue")
    public void handleProgressUpdate(RagProcessingProgress progress) {
        try {
            progressService.saveProgress(progress);
            log.debug("进度已保存: taskId={}, status={}, progress={}",
                progress.getTaskId(), progress.getStatus(), progress.getProgress());
        } catch (Exception e) {
            log.error("保存进度失败: taskId={}",
                progress.getTaskId() != null ? progress.getTaskId() : "unknown", e);
        }
    }
}
