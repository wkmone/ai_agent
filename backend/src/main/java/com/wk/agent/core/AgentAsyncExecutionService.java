package com.wk.agent.core;

import com.wk.agent.service.AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Service
public class AgentAsyncExecutionService {

    private static final Logger log = LoggerFactory.getLogger(AgentAsyncExecutionService.class);

    @Value("${agent.async.thread-pool-size:10}")
    private int threadPoolSize;

    @Value("${agent.async.default-timeout:300}")
    private int defaultTimeoutSeconds;

    @Autowired
    private AgentService agentService;

    private ThreadPoolTaskExecutor executorService;
    private final Map<String, CompletableFuture<AgentResult>> pendingFutures = new ConcurrentHashMap<>();
    private final Map<String, AsyncExecutionContext> executionContexts = new ConcurrentHashMap<>();
    private final List<AsyncExecutionListener> listeners = new ArrayList<>();

    @jakarta.annotation.PostConstruct
    public void init() {
        executorService = new ThreadPoolTaskExecutor();
        executorService.setCorePoolSize(threadPoolSize);
        executorService.setMaxPoolSize(threadPoolSize * 2);
        executorService.setQueueCapacity(100);
        executorService.setThreadNamePrefix("agent-async-");
        executorService.initialize();
        
        log.info("Agent 异步执行服务初始化完成，线程池大小: {}", threadPoolSize);
    }

    public static class AsyncExecutionContext {
        private final String executionId;
        private final String agentId;
        private final AgentTask task;
        private volatile AsyncExecutionStatus status;
        private volatile double progress;
        private volatile String currentPhase;
        private volatile String message;
        private AgentResult result;
        private Throwable error;
        private final long startTime;
        private volatile long endTime;

        public AsyncExecutionContext(String executionId, String agentId, AgentTask task) {
            this.executionId = executionId;
            this.agentId = agentId;
            this.task = task;
            this.status = AsyncExecutionStatus.PENDING;
            this.progress = 0.0;
            this.startTime = System.currentTimeMillis();
        }

        public String getExecutionId() { return executionId; }
        public String getAgentId() { return agentId; }
        public AgentTask getTask() { return task; }
        public AsyncExecutionStatus getStatus() { return status; }
        public void setStatus(AsyncExecutionStatus status) { this.status = status; }
        public double getProgress() { return progress; }
        public void setProgress(double progress) { this.progress = progress; }
        public String getCurrentPhase() { return currentPhase; }
        public void setCurrentPhase(String currentPhase) { this.currentPhase = currentPhase; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public AgentResult getResult() { return result; }
        public void setResult(AgentResult result) { this.result = result; }
        public Throwable getError() { return error; }
        public void setError(Throwable error) { this.error = error; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }
        public long getDuration() { return endTime > 0 ? endTime - startTime : System.currentTimeMillis() - startTime; }
    }

    public static class BatchExecutionItem {
        private final String agentId;
        private final AgentTask task;
        
        public BatchExecutionItem(String agentId, AgentTask task) {
            this.agentId = agentId;
            this.task = task;
        }
        
        public String getAgentId() { return agentId; }
        public AgentTask getTask() { return task; }
    }

    public static class SequentialExecutionItem {
        private final String agentId;
        private final AgentTask task;
        private final boolean usePreviousResult;
        
        public SequentialExecutionItem(String agentId, AgentTask task, boolean usePreviousResult) {
            this.agentId = agentId;
            this.task = task;
            this.usePreviousResult = usePreviousResult;
        }
        
        public String getAgentId() { return agentId; }
        public AgentTask getTask() { return task; }
        public boolean isUsePreviousResult() { return usePreviousResult; }
    }

    public interface AsyncExecutionCallback {
        void onComplete(AgentResult result);
        void onError(Throwable error);
    }

    public enum AsyncExecutionStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED,
        TIMEOUT
    }

    public enum AsyncExecutionEvent {
        STARTED,
        RUNNING,
        PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED,
        TIMEOUT
    }

    @Async
    public CompletableFuture<AgentResult> executeAsync(String agentId, AgentTask task) {
        return executeAsync(agentId, task, defaultTimeoutSeconds);
    }

    @Async
    public CompletableFuture<AgentResult> executeAsync(String agentId, AgentTask task, int timeoutSeconds) {
        String executionId = "exec_" + UUID.randomUUID().toString().substring(0, 8);
        
        AsyncExecutionContext context = new AsyncExecutionContext(executionId, agentId, task);
        executionContexts.put(executionId, context);

        notifyListeners(context, AsyncExecutionEvent.STARTED);

        CompletableFuture<AgentResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                context.setStatus(AsyncExecutionStatus.RUNNING);
                context.setCurrentPhase("执行中");
                context.setProgress(0.1);
                
                notifyListeners(context, AsyncExecutionEvent.RUNNING);

                var agent = agentService.getAgentById(agentId);
                if (agent == null) {
                    throw new IllegalStateException("Agent not found: " + agentId);
                }

                context.setProgress(0.3);
                AgentResult result = agent.execute(task);
                context.setProgress(0.9);

                context.setResult(result);
                context.setStatus(AsyncExecutionStatus.COMPLETED);
                context.setEndTime(System.currentTimeMillis());
                context.setProgress(1.0);
                
                notifyListeners(context, AsyncExecutionEvent.COMPLETED);
                
                return result;

            } catch (Exception e) {
                context.setError(e);
                context.setStatus(AsyncExecutionStatus.FAILED);
                context.setMessage(e.getMessage());
                context.setEndTime(System.currentTimeMillis());
                
                notifyListeners(context, AsyncExecutionEvent.FAILED);
                
                throw new CompletionException(e);
            }
        }, executorService.getThreadPoolExecutor()).orTimeout(timeoutSeconds, TimeUnit.SECONDS);

        future.exceptionally(ex -> {
            if (ex instanceof TimeoutException || ex.getCause() instanceof TimeoutException) {
                context.setStatus(AsyncExecutionStatus.TIMEOUT);
                context.setMessage("执行超时");
                context.setEndTime(System.currentTimeMillis());
                notifyListeners(context, AsyncExecutionEvent.TIMEOUT);
                return new AgentResult("执行超时", false);
            }
            return new AgentResult(ex.getMessage(), false);
        });

        pendingFutures.put(executionId, future);
        
        future.whenComplete((result, ex) -> {
            pendingFutures.remove(executionId);
        });

        return future;
    }

    public CompletableFuture<AgentResult> executeWithCallback(String agentId, AgentTask task, 
            AsyncExecutionCallback callback) {
        CompletableFuture<AgentResult> future = executeAsync(agentId, task);
        
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                callback.onError(ex);
            } else {
                callback.onComplete(result);
            }
        });

        return future;
    }

    public CompletableFuture<List<AgentResult>> executeBatch(List<BatchExecutionItem> items) {
        List<CompletableFuture<AgentResult>> futures = items.stream()
                .map(item -> executeAsync(item.getAgentId(), item.getTask()))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    public CompletableFuture<AgentResult> executeSequential(List<SequentialExecutionItem> items) {
        CompletableFuture<AgentResult> future = CompletableFuture.completedFuture(null);
        
        for (SequentialExecutionItem item : items) {
            future = future.thenCompose(prevResult -> {
                if (prevResult != null && !prevResult.isSuccess()) {
                    return CompletableFuture.completedFuture(prevResult);
                }
                
                AgentTask task = item.getTask();
                if (prevResult != null && item.isUsePreviousResult()) {
                    if (task.getParameters() == null) {
                        task.setParameters(new HashMap<>());
                    }
                    task.getParameters().put("previousResult", prevResult.getMessage());
                }
                
                return executeAsync(item.getAgentId(), task);
            });
        }
        
        return future;
    }

    public boolean cancelExecution(String executionId) {
        CompletableFuture<AgentResult> future = pendingFutures.get(executionId);
        if (future != null && !future.isDone()) {
            boolean cancelled = future.cancel(true);
            if (cancelled) {
                AsyncExecutionContext context = executionContexts.get(executionId);
                if (context != null) {
                    context.setStatus(AsyncExecutionStatus.CANCELLED);
                    context.setEndTime(System.currentTimeMillis());
                    notifyListeners(context, AsyncExecutionEvent.CANCELLED);
                }
            }
            return cancelled;
        }
        return false;
    }

    public AsyncExecutionContext getExecutionContext(String executionId) {
        return executionContexts.get(executionId);
    }

    public Map<String, AsyncExecutionContext> getAllExecutionContexts() {
        return new HashMap<>(executionContexts);
    }

    public List<AsyncExecutionContext> getRunningExecutions() {
        return executionContexts.values().stream()
                .filter(c -> c.getStatus() == AsyncExecutionStatus.RUNNING || 
                            c.getStatus() == AsyncExecutionStatus.PENDING)
                .toList();
    }

    public void updateProgress(String executionId, double progress, String phase) {
        AsyncExecutionContext context = executionContexts.get(executionId);
        if (context != null) {
            context.setProgress(progress);
            context.setCurrentPhase(phase);
            notifyListeners(context, AsyncExecutionEvent.PROGRESS);
        }
    }

    public void addListener(AsyncExecutionListener listener) {
        listeners.add(listener);
    }

    public void removeListener(AsyncExecutionListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(AsyncExecutionContext context, AsyncExecutionEvent event) {
        for (AsyncExecutionListener listener : listeners) {
            try {
                listener.onExecutionEvent(context, event);
            } catch (Exception e) {
                log.error("异步执行监听器执行失败", e);
            }
        }
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        
        stats.put("totalExecutions", executionContexts.size());
        stats.put("pendingExecutions", pendingFutures.size());
        stats.put("threadPoolSize", threadPoolSize);
        stats.put("defaultTimeoutSeconds", defaultTimeoutSeconds);

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (AsyncExecutionStatus status : AsyncExecutionStatus.values()) {
            statusCounts.put(status.name(), 0L);
        }
        
        for (AsyncExecutionContext context : executionContexts.values()) {
            String statusName = context.getStatus().name();
            statusCounts.put(statusName, statusCounts.get(statusName) + 1);
        }
        stats.put("statusCounts", statusCounts);

        return stats;
    }

    @FunctionalInterface
    public interface AsyncExecutionListener {
        void onExecutionEvent(AsyncExecutionContext context, AsyncExecutionEvent event);
    }
}
