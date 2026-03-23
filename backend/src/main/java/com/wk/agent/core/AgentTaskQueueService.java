package com.wk.agent.core;

import com.wk.agent.core.AgentAsyncExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AgentTaskQueueService {

    private static final Logger log = LoggerFactory.getLogger(AgentTaskQueueService.class);

    @Value("${agent.task-queue.max-size:1000}")
    private int maxQueueSize;

    @Value("${agent.task-queue.max-retries:3}")
    private int maxRetries;

    @Autowired
    private AgentAsyncExecutionService asyncExecutionService;

    private final PriorityBlockingQueue<QueuedTask> memoryQueue = new PriorityBlockingQueue<>();
    private final Map<String, QueuedTask> taskRegistry = new ConcurrentHashMap<>();
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private final Map<String, TaskProcessor> processors = new HashMap<>();
    private final List<TaskQueueListener> listeners = new ArrayList<>();

    public static class QueuedTask implements Comparable<QueuedTask> {
        private final String taskId;
        private final String agentId;
        private final AgentTask task;
        private final TaskPriority priority;
        private final LocalDateTime createdAt;
        private volatile int retryCount;
        private volatile String status;
        private volatile LocalDateTime scheduledAt;
        private volatile AgentResult result;

        public QueuedTask(String taskId, String agentId, AgentTask task, TaskPriority priority) {
            this.taskId = taskId;
            this.agentId = agentId;
            this.task = task;
            this.priority = priority;
            this.createdAt = LocalDateTime.now();
            this.retryCount = 0;
            this.status = "PENDING";
        }

        @Override
        public int compareTo(QueuedTask other) {
            int priorityCompare = other.priority.ordinal() - this.priority.ordinal();
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return this.createdAt.compareTo(other.createdAt);
        }

        public String getTaskId() { return taskId; }
        public String getAgentId() { return agentId; }
        public AgentTask getTask() { return task; }
        public TaskPriority getPriority() { return priority; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public int getRetryCount() { return retryCount; }
        public void incrementRetry() { this.retryCount++; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public LocalDateTime getScheduledAt() { return scheduledAt; }
        public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
        public AgentResult getResult() { return result; }
        public void setResult(AgentResult result) { this.result = result; }
    }

    public enum TaskPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }

    public interface TaskProcessor {
        CompletableFuture<AgentResult> process(AgentTask task);
    }

    public String enqueue(String agentId, AgentTask task) {
        return enqueue(agentId, task, TaskPriority.NORMAL);
    }

    public String enqueue(String agentId, AgentTask task, TaskPriority priority) {
        if (memoryQueue.size() >= maxQueueSize) {
            throw new IllegalStateException("任务队列已满，无法添加新任务");
        }

        String taskId = "task_" + UUID.randomUUID().toString().substring(0, 8);
        QueuedTask queuedTask = new QueuedTask(taskId, agentId, task, priority);

        taskRegistry.put(taskId, queuedTask);
        memoryQueue.offer(queuedTask);

        log.info("任务入队: taskId={}, agentId={}, priority={}", taskId, agentId, priority);
        notifyListeners(taskId, "ENQUEUED", null);

        return taskId;
    }

    public String enqueueWithDelay(String agentId, AgentTask task, TaskPriority priority, 
            LocalDateTime scheduledTime) {
        String taskId = "task_" + UUID.randomUUID().toString().substring(0, 8);
        QueuedTask queuedTask = new QueuedTask(taskId, agentId, task, priority);
        queuedTask.setStatus("SCHEDULED");
        queuedTask.setScheduledAt(scheduledTime);

        taskRegistry.put(taskId, queuedTask);

        log.info("定时任务入队: taskId={}, scheduledTime={}", taskId, scheduledTime);
        notifyListeners(taskId, "SCHEDULED", scheduledTime);

        return taskId;
    }

    @Scheduled(fixedRateString = "${agent.task-queue.process-interval:5000}")
    public void processQueue() {
        if (!processing.compareAndSet(false, true)) {
            return;
        }

        try {
            processScheduledTasks();
            processMemoryQueue();
        } finally {
            processing.set(false);
        }
    }

    private void processScheduledTasks() {
        LocalDateTime now = LocalDateTime.now();
        
        for (QueuedTask task : taskRegistry.values()) {
            if ("SCHEDULED".equals(task.getStatus()) && 
                task.getScheduledAt() != null && 
                !task.getScheduledAt().isAfter(now)) {
                
                task.setStatus("PENDING");
                memoryQueue.offer(task);
                log.info("定时任务转入内存队列: taskId={}", task.getTaskId());
            }
        }
    }

    private void processMemoryQueue() {
        int processed = 0;
        int maxBatch = 10;

        while (!memoryQueue.isEmpty() && processed < maxBatch) {
            QueuedTask queuedTask = memoryQueue.poll();
            if (queuedTask == null) {
                break;
            }

            try {
                processTask(queuedTask);
                processed++;
            } catch (Exception e) {
                log.error("处理任务失败: taskId={}", queuedTask.getTaskId(), e);
                handleTaskFailure(queuedTask, e);
            }
        }
    }

    private void processTask(QueuedTask queuedTask) {
        String taskId = queuedTask.getTaskId();
        queuedTask.setStatus("PROCESSING");

        log.info("开始处理任务: taskId={}, agentId={}", taskId, queuedTask.getAgentId());

        TaskProcessor processor = processors.get(queuedTask.getAgentId());
        CompletableFuture<AgentResult> future;
        
        if (processor != null) {
            future = processor.process(queuedTask.getTask());
        } else {
            future = asyncExecutionService.executeAsync(queuedTask.getAgentId(), queuedTask.getTask());
        }

        future.thenAccept(result -> handleTaskCompletion(queuedTask, result))
             .exceptionally(ex -> {
                 handleTaskFailure(queuedTask, ex);
                 return null;
             });
    }

    private void handleTaskCompletion(QueuedTask queuedTask, AgentResult result) {
        String taskId = queuedTask.getTaskId();
        
        if (result.isSuccess()) {
            queuedTask.setStatus("COMPLETED");
            queuedTask.setResult(result);
            
            log.info("任务完成: taskId={}", taskId);
            notifyListeners(taskId, "COMPLETED", result);
        } else {
            handleTaskFailure(queuedTask, new Exception(result.getMessage()));
        }
    }

    private void handleTaskFailure(QueuedTask queuedTask, Throwable error) {
        String taskId = queuedTask.getTaskId();
        queuedTask.incrementRetry();

        if (queuedTask.getRetryCount() < maxRetries) {
            queuedTask.setStatus("RETRY");
            
            memoryQueue.offer(queuedTask);
            log.warn("任务重试: taskId={}, retryCount={}", taskId, queuedTask.getRetryCount());
            notifyListeners(taskId, "RETRY", error.getMessage());
        } else {
            queuedTask.setStatus("FAILED");
            queuedTask.setResult(new AgentResult(error.getMessage(), false));
            
            log.error("任务最终失败: taskId={}", taskId, error);
            notifyListeners(taskId, "FAILED", error.getMessage());
        }
    }

    public boolean cancelTask(String taskId) {
        QueuedTask task = taskRegistry.get(taskId);
        if (task == null) {
            return false;
        }
        
        if ("PENDING".equals(task.getStatus()) || "SCHEDULED".equals(task.getStatus())) {
            task.setStatus("CANCELLED");
            memoryQueue.remove(task);
            log.info("任务取消: taskId={}", taskId);
            notifyListeners(taskId, "CANCELLED", null);
            return true;
        }
        
        return false;
    }

    public Optional<QueuedTask> getTask(String taskId) {
        return Optional.ofNullable(taskRegistry.get(taskId));
    }

    public List<QueuedTask> getPendingTasks() {
        return new ArrayList<>(memoryQueue);
    }

    public List<QueuedTask> getTasksByStatus(String status) {
        return taskRegistry.values().stream()
                .filter(t -> status.equals(t.getStatus()))
                .toList();
    }

    public int getQueueSize() {
        return memoryQueue.size();
    }

    public Map<String, Object> getQueueStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        
        stats.put("totalTasks", taskRegistry.size());
        stats.put("pendingInQueue", memoryQueue.size());
        stats.put("maxQueueSize", maxQueueSize);
        stats.put("maxRetries", maxRetries);

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (QueuedTask task : taskRegistry.values()) {
            statusCounts.merge(task.getStatus(), 1L, Long::sum);
        }
        stats.put("statusCounts", statusCounts);

        return stats;
    }

    public void registerProcessor(String agentId, TaskProcessor processor) {
        processors.put(agentId, processor);
        log.info("注册任务处理器: agentId={}", agentId);
    }

    public void unregisterProcessor(String agentId) {
        processors.remove(agentId);
        log.info("注销任务处理器: agentId={}", agentId);
    }

    public void addListener(TaskQueueListener listener) {
        listeners.add(listener);
    }

    public void removeListener(TaskQueueListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(String taskId, String event, Object data) {
        for (TaskQueueListener listener : listeners) {
            try {
                listener.onTaskEvent(taskId, event, data);
            } catch (Exception e) {
                log.error("任务队列监听器执行失败", e);
            }
        }
    }

    public void clearCompletedTasks() {
        taskRegistry.values().removeIf(t -> "COMPLETED".equals(t.getStatus()));
        log.info("清理已完成任务");
    }

    public void clearFailedTasks() {
        taskRegistry.values().removeIf(t -> "FAILED".equals(t.getStatus()));
        log.info("清理失败任务");
    }

    public void clearCancelledTasks() {
        taskRegistry.values().removeIf(t -> "CANCELLED".equals(t.getStatus()));
        log.info("清理取消任务");
    }

    @FunctionalInterface
    public interface TaskQueueListener {
        void onTaskEvent(String taskId, String event, Object data);
    }
}
