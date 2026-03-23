package com.wk.agent.orchestration;

import com.wk.agent.core.AbstractAgent;
import com.wk.agent.core.AgentResult;
import com.wk.agent.core.AgentTask;
import com.wk.agent.service.AgentService;
import com.wk.agent.service.MultiLayerMemoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final Map<String, OrchestrationTask> taskQueue = new ConcurrentHashMap<>();
    private final Map<String, OrchestrationResult> completedTasks = new ConcurrentHashMap<>();

    @Autowired
    private AgentService agentService;

    @Autowired
    private MultiLayerMemoryManager memoryManager;

    public enum CollaborationMode {
        HIERARCHICAL,
        SEQUENTIAL,
        PARALLEL,
        DEBATE,
        COMPETITIVE
    }

    public OrchestrationTask submitTask(String description) {
        return submitTask(description, CollaborationMode.SEQUENTIAL);
    }

    public OrchestrationTask submitTask(String description, CollaborationMode mode) {
        OrchestrationTask task = OrchestrationTask.create(description);
        task.addContext("collaborationMode", mode);
        taskQueue.put(task.getTaskId(), task);
        log.info("提交任务: {} 模式: {}", task.getTaskId(), mode);
        return task;
    }

    public OrchestrationTask submitTask(OrchestrationTask task) {
        taskQueue.put(task.getTaskId(), task);
        log.info("提交任务: {} 类型: {}", task.getTaskId(), task.getTaskType());
        return task;
    }

    public OrchestrationResult executeTask(String taskId) {
        OrchestrationTask task = taskQueue.get(taskId);
        if (task == null) {
            return OrchestrationResult.failure(taskId, "任务不存在: " + taskId);
        }

        CollaborationMode mode = (CollaborationMode) task.getContext()
                .getOrDefault("collaborationMode", CollaborationMode.SEQUENTIAL);

        return executeWithMode(task, mode);
    }

    public OrchestrationResult executeWithMode(OrchestrationTask task, CollaborationMode mode) {
        log.info("执行任务: {} 模式: {}", task.getTaskId(), mode);
        task.markRunning();

        OrchestrationResult result = new OrchestrationResult();
        result.setTaskId(task.getTaskId());
        result.setSessionId(task.getSessionId());

        try {
            switch (mode) {
                case HIERARCHICAL:
                    result = executeHierarchical(task);
                    break;
                case SEQUENTIAL:
                    result = executeSequential(task);
                    break;
                case PARALLEL:
                    result = executeParallel(task);
                    break;
                case DEBATE:
                    result = executeDebate(task);
                    break;
                case COMPETITIVE:
                    result = executeCompetitive(task);
                    break;
                default:
                    result = executeSequential(task);
            }

            task.markCompleted();
            result.setSuccess(true);

        } catch (Exception e) {
            log.error("任务执行失败: {}", e.getMessage());
            task.markFailed();
            result.setSuccess(false);
            result.setError(e.getMessage());

            if (task.canRetry()) {
                task.incrementRetry();
                log.info("任务将重试: {} 第{}次", task.getTaskId(), task.getRetryCount());
                return executeWithMode(task, mode);
            }
        }

        result.calculateDuration();
        result.setEndTime(LocalDateTime.now());
        completedTasks.put(task.getTaskId(), result);

        return result;
    }

    private OrchestrationResult executeHierarchical(OrchestrationTask task) {
        OrchestrationResult result = new OrchestrationResult();
        result.setTaskId(task.getTaskId());

        AbstractAgent plannerAgent = selectBestAgent(task, "planner");
        if (plannerAgent == null) {
            plannerAgent = agentService.getAllAgents().stream()
                    .filter(a -> a.getName().toLowerCase().contains("plan"))
                    .findFirst()
                    .orElse(null);
        }

        if (plannerAgent == null) {
            return OrchestrationResult.failure(task.getTaskId(), "没有可用的规划Agent");
        }

        String planPrompt = String.format(
                "请分析以下任务并分解为子任务:\n%s\n\n请列出需要执行的步骤。",
                task.getDescription()
        );

        AgentResult planResult = plannerAgent.processMessage(task.getSessionId(), planPrompt);

        OrchestrationResult.AgentExecution planExecution = OrchestrationResult.AgentExecution.of(
                plannerAgent.getId(), plannerAgent.getName(), "plan"
        );
        planExecution.setSuccess(planResult.isSuccess());
        planExecution.setOutput(planResult.getMessage());
        result.addExecution(planExecution);

        List<String> subtasks = parseSubtasks(planResult.getMessage());

        for (int i = 0; i < subtasks.size(); i++) {
            String subtask = subtasks.get(i);
            OrchestrationTask subTask = OrchestrationTask.create(subtask);
            subTask.setSessionId(task.getSessionId());
            subTask.addContext("parentTaskId", task.getTaskId());

            AbstractAgent workerAgent = selectBestAgent(subTask, "worker");

            if (workerAgent != null) {
                long execStart = System.currentTimeMillis();
                AgentResult subResult = workerAgent.processMessage(task.getSessionId(), subtask);
                long execDuration = System.currentTimeMillis() - execStart;

                OrchestrationResult.AgentExecution exec = OrchestrationResult.AgentExecution.of(
                        workerAgent.getId(), workerAgent.getName(), "execute_subtask_" + i
                );
                exec.setSuccess(subResult.isSuccess());
                exec.setOutput(subResult.getMessage());
                exec.setDurationMs(execDuration);
                result.addExecution(exec);
            }
        }

        result.setResult("层级协作完成，共执行 " + subtasks.size() + " 个子任务");
        return result;
    }

    private OrchestrationResult executeSequential(OrchestrationTask task) {
        OrchestrationResult result = new OrchestrationResult();
        result.setTaskId(task.getTaskId());

        List<AbstractAgent> agents = selectAgentsForTask(task);
        if (agents.isEmpty()) {
            return OrchestrationResult.failure(task.getTaskId(), "没有可用的Agent");
        }

        String currentInput = task.getDescription();

        for (AbstractAgent agent : agents) {
            long execStart = System.currentTimeMillis();

            String context = memoryManager.buildContextPrompt(task.getSessionId(), currentInput);
            String agentInput = context + "\n\n当前任务: " + currentInput;

            AgentResult agentResult = agent.processMessage(task.getSessionId(), agentInput);
            long execDuration = System.currentTimeMillis() - execStart;

            OrchestrationResult.AgentExecution execution = OrchestrationResult.AgentExecution.of(
                    agent.getId(), agent.getName(), "sequential_process"
            );
            execution.setSuccess(agentResult.isSuccess());
            execution.setOutput(agentResult.getMessage());
            execution.setDurationMs(execDuration);
            result.addExecution(execution);

            currentInput = agentResult.getMessage();
        }

        result.setResult(currentInput);
        return result;
    }

    private OrchestrationResult executeParallel(OrchestrationTask task) {
        OrchestrationResult result = new OrchestrationResult();
        result.setTaskId(task.getTaskId());

        List<AbstractAgent> agents = selectAgentsForTask(task);
        if (agents.isEmpty()) {
            return OrchestrationResult.failure(task.getTaskId(), "没有可用的Agent");
        }

        List<CompletableFuture<OrchestrationResult.AgentExecution>> futures = agents.stream()
                .map(agent -> CompletableFuture.supplyAsync(() -> {
                    long execStart = System.currentTimeMillis();
                    AgentResult agentResult = agent.processMessage(task.getSessionId(), task.getDescription());
                    long execDuration = System.currentTimeMillis() - execStart;

                    OrchestrationResult.AgentExecution execution = OrchestrationResult.AgentExecution.of(
                            agent.getId(), agent.getName(), "parallel_process"
                    );
                    execution.setSuccess(agentResult.isSuccess());
                    execution.setOutput(agentResult.getMessage());
                    execution.setDurationMs(execDuration);
                    return execution;
                }, executorService))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<String> outputs = new ArrayList<>();
        for (CompletableFuture<OrchestrationResult.AgentExecution> future : futures) {
            try {
                OrchestrationResult.AgentExecution execution = future.get();
                result.addExecution(execution);
                if (execution.getOutput() != null) {
                    outputs.add(String.format("[%s] %s",
                            execution.getAgentName(), execution.getOutput()));
                }
            } catch (Exception e) {
                log.warn("并行执行异常: {}", e.getMessage());
            }
        }

        String aggregatedResult = aggregateResults(outputs);
        result.setResult(aggregatedResult);
        return result;
    }

    private OrchestrationResult executeDebate(OrchestrationTask task) {
        OrchestrationResult result = new OrchestrationResult();
        result.setTaskId(task.getTaskId());

        List<AbstractAgent> agents = selectAgentsForTask(task);
        if (agents.size() < 2) {
            return executeSequential(task);
        }

        AbstractAgent agent1 = agents.get(0);
        AbstractAgent agent2 = agents.get(1);

        int rounds = 3;
        List<String> positions = new ArrayList<>();
        String currentTopic = task.getDescription();

        for (int round = 0; round < rounds; round++) {
            long execStart1 = System.currentTimeMillis();
            AgentResult response1 = agent1.processMessage(
                    task.getSessionId(),
                    String.format("辩论话题: %s\n请提出你的观点和分析。", currentTopic)
            );

            OrchestrationResult.AgentExecution exec1 = OrchestrationResult.AgentExecution.of(
                    agent1.getId(), agent1.getName(), "debate_round_" + round + "_agent1"
            );
            exec1.setSuccess(response1.isSuccess());
            exec1.setOutput(response1.getMessage());
            exec1.setDurationMs(System.currentTimeMillis() - execStart1);
            result.addExecution(exec1);

            long execStart2 = System.currentTimeMillis();
            AgentResult response2 = agent2.processMessage(
                    task.getSessionId(),
                    String.format("辩论话题: %s\n对方观点: %s\n请提出你的反驳或补充。",
                            currentTopic, response1.getMessage())
            );

            OrchestrationResult.AgentExecution exec2 = OrchestrationResult.AgentExecution.of(
                    agent2.getId(), agent2.getName(), "debate_round_" + round + "_agent2"
            );
            exec2.setSuccess(response2.isSuccess());
            exec2.setOutput(response2.getMessage());
            exec2.setDurationMs(System.currentTimeMillis() - execStart2);
            result.addExecution(exec2);

            positions.add(String.format("Round %d:\nAgent1: %s\nAgent2: %s",
                    round + 1, response1.getMessage(), response2.getMessage()));
        }

        String consensusPrompt = String.format(
                "基于以下辩论内容，请总结出最佳结论:\n%s",
                String.join("\n\n", positions)
        );

        long execStart = System.currentTimeMillis();
        AgentResult consensus = agent1.processMessage(task.getSessionId(), consensusPrompt);

        OrchestrationResult.AgentExecution consensusExec = OrchestrationResult.AgentExecution.of(
                agent1.getId(), agent1.getName(), "debate_consensus"
        );
        consensusExec.setSuccess(consensus.isSuccess());
        consensusExec.setOutput(consensus.getMessage());
        consensusExec.setDurationMs(System.currentTimeMillis() - execStart);
        result.addExecution(consensusExec);

        result.setResult(consensus.getMessage());
        return result;
    }

    private OrchestrationResult executeCompetitive(OrchestrationTask task) {
        OrchestrationResult result = new OrchestrationResult();
        result.setTaskId(task.getTaskId());

        List<AbstractAgent> agents = selectAgentsForTask(task);
        if (agents.isEmpty()) {
            return OrchestrationResult.failure(task.getTaskId(), "没有可用的Agent");
        }

        List<AgentResponse> responses = new ArrayList<>();

        for (AbstractAgent agent : agents) {
            long execStart = System.currentTimeMillis();
            AgentResult agentResult = agent.processMessage(task.getSessionId(), task.getDescription());
            long execDuration = System.currentTimeMillis() - execStart;

            double score = evaluateResponse(agentResult.getMessage(), task);

            OrchestrationResult.AgentExecution execution = OrchestrationResult.AgentExecution.of(
                    agent.getId(), agent.getName(), "competitive_process"
            );
            execution.setSuccess(agentResult.isSuccess());
            execution.setOutput(agentResult.getMessage());
            execution.setDurationMs(execDuration);
            result.addExecution(execution);

            responses.add(new AgentResponse(agent, agentResult.getMessage(), score));
        }

        AgentResponse best = responses.stream()
                .max(Comparator.comparingDouble(AgentResponse::getScore))
                .orElse(responses.get(0));

        result.setResult(best.getResponse());
        result.addMetadata("bestAgentId", best.getAgent().getId());
        result.addMetadata("bestScore", best.getScore());

        return result;
    }

    private AbstractAgent selectBestAgent(OrchestrationTask task, String role) {
        List<AbstractAgent> agents = agentService.getAllAgents();
        if (agents.isEmpty()) {
            return null;
        }

        String description = task.getDescription().toLowerCase();

        if (description.contains("规划") || description.contains("计划") || description.contains("plan")) {
            return agents.stream()
                    .filter(a -> a.getName().toLowerCase().contains("plan"))
                    .findFirst()
                    .orElse(agents.get(0));
        }

        if (description.contains("分析") || description.contains("analyze")) {
            return agents.stream()
                    .filter(a -> a.getName().toLowerCase().contains("react") ||
                                 a.getName().toLowerCase().contains("reflection"))
                    .findFirst()
                    .orElse(agents.get(0));
        }

        if (description.contains("执行") || description.contains("execute")) {
            return agents.stream()
                    .filter(a -> a.getName().toLowerCase().contains("simple") ||
                                 a.getName().toLowerCase().contains("function"))
                    .findFirst()
                    .orElse(agents.get(0));
        }

        return agents.get(0);
    }

    private List<AbstractAgent> selectAgentsForTask(OrchestrationTask task) {
        List<AbstractAgent> allAgents = agentService.getAllAgents();
        if (allAgents.isEmpty()) {
            return Collections.emptyList();
        }

        if (allAgents.size() <= 2) {
            return allAgents;
        }

        return allAgents.stream()
                .limit(3)
                .collect(Collectors.toList());
    }

    private List<String> parseSubtasks(String planResult) {
        List<String> subtasks = new ArrayList<>();
        if (planResult == null) {
            return subtasks;
        }

        String[] lines = planResult.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.matches("^\\d+[.、)].*") || line.matches("^[-*•].*")) {
                String subtask = line.replaceAll("^[\\d]+[.、)]\\s*", "")
                                     .replaceAll("^[-*•]\\s*", "");
                if (subtask.length() > 5) {
                    subtasks.add(subtask);
                }
            }
        }

        if (subtasks.isEmpty()) {
            subtasks.add(planResult);
        }

        return subtasks;
    }

    private String aggregateResults(List<String> results) {
        if (results.isEmpty()) {
            return "";
        }

        if (results.size() == 1) {
            return results.get(0);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("综合分析结果:\n\n");

        for (int i = 0; i < results.size(); i++) {
            sb.append(String.format("【分析%d】\n%s\n\n", i + 1, results.get(i)));
        }

        return sb.toString();
    }

    private double evaluateResponse(String response, OrchestrationTask task) {
        double score = 0.5;

        if (response == null || response.isEmpty()) {
            return 0.0;
        }

        score += Math.min(response.length() / 500.0, 0.2);

        String[] sentences = response.split("[。.!?！？]");
        score += Math.min(sentences.length * 0.02, 0.15);

        String description = task.getDescription().toLowerCase();
        String[] keywords = description.split("\\s+");
        for (String keyword : keywords) {
            if (keyword.length() > 2 && response.toLowerCase().contains(keyword)) {
                score += 0.02;
            }
        }

        return Math.min(score, 1.0);
    }

    public OrchestrationTask getTask(String taskId) {
        return taskQueue.get(taskId);
    }

    public OrchestrationResult getResult(String taskId) {
        return completedTasks.get(taskId);
    }

    public List<OrchestrationTask> getPendingTasks() {
        return taskQueue.values().stream()
                .filter(t -> t.getStatus() == OrchestrationTask.TaskStatus.PENDING)
                .sorted(Comparator.comparing(OrchestrationTask::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<OrchestrationResult> getRecentResults(int limit) {
        return completedTasks.values().stream()
                .filter(r -> r.getEndTime() != null)
                .sorted(Comparator.comparing(OrchestrationResult::getEndTime).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("pendingTasks", taskQueue.values().stream()
                .filter(t -> t.getStatus() == OrchestrationTask.TaskStatus.PENDING)
                .count());
        stats.put("runningTasks", taskQueue.values().stream()
                .filter(t -> t.getStatus() == OrchestrationTask.TaskStatus.RUNNING)
                .count());
        stats.put("completedTasks", completedTasks.size());
        stats.put("successRate", completedTasks.isEmpty() ? 0 :
                completedTasks.values().stream().filter(OrchestrationResult::isSuccess).count() * 100.0 / completedTasks.size());
        return stats;
    }

    private static class AgentResponse {
        private final AbstractAgent agent;
        private final String response;
        private final double score;

        public AgentResponse(AbstractAgent agent, String response, double score) {
            this.agent = agent;
            this.response = response;
            this.score = score;
        }

        public AbstractAgent getAgent() {
            return agent;
        }

        public String getResponse() {
            return response;
        }

        public double getScore() {
            return score;
        }
    }
}