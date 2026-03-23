package com.wk.agent.impl;

import com.wk.agent.core.AbstractAgent;
import com.wk.agent.core.AgentResult;
import com.wk.agent.core.AgentStatus;
import com.wk.agent.core.AgentTask;
import com.wk.agent.service.MultiLayerMemoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CodebaseMaintainer extends AbstractAgent {

    private static final Logger log = LoggerFactory.getLogger(CodebaseMaintainer.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter SESSION_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int MAX_SESSION_AGE_HOURS = 24;
    private static final int MAX_SESSIONS = 100;
    private static final int MAX_CONTEXT_LENGTH = 8000;

    private final Map<String, SessionStats> sessionStatsMap = new ConcurrentHashMap<>();

    private String currentSessionId;
    private String projectName;
    private String codebasePath;

    public CodebaseMaintainer() {
        super("codebase-maintainer", "代码库维护助手",
              "长程智能体，整合记忆系统，实现跨会话的代码库维护任务管理");
    }

    public void initialize(String projectName, String codebasePath) {
        this.projectName = projectName;
        this.codebasePath = codebasePath;
        this.currentSessionId = "session_" + LocalDateTime.now().format(SESSION_FORMATTER);

        SessionStats stats = new SessionStats();
        stats.setSessionStart(LocalDateTime.now());
        stats.setCommandsExecuted(0);
        stats.setNotesCreated(0);
        stats.setIssuesFound(0);
        sessionStatsMap.put(currentSessionId, stats);

        setStatus(AgentStatus.INITIALIZED);
        log.info("代码库维护助手已初始化: project={}, path={}", projectName, codebasePath);
    }

    @Override
    public void initialize() {
        setStatus(AgentStatus.INITIALIZED);
    }

    @Override
    public AgentResult execute(AgentTask task) {
        if (getStatus() != AgentStatus.INITIALIZED && getStatus() != AgentStatus.RUNNING) {
            return new AgentResult("Agent未就绪", false);
        }

        String mode = determineMode(task);
        return processMessage(task.getSessionId(), task.getTaskContent());
    }

    @Override
    public AgentResult processMessage(String sessionId, String message) {
        if (currentSessionId == null) {
            if (sessionId != null && !sessionId.isEmpty()) {
                currentSessionId = sessionId;
            } else {
                currentSessionId = "session_" + LocalDateTime.now().format(SESSION_FORMATTER);
            }
        }

        cleanupExpiredSessions();

        setStatus(AgentStatus.RUNNING);
        SessionStats stats = sessionStatsMap.computeIfAbsent(currentSessionId,
                k -> new SessionStats());

        try {
            String memoryContext = memoryManager.buildContextPrompt(currentSessionId, message);

            String context = buildSystemInstructions("auto") + "\n\n" + memoryContext;

            context = truncate(context, MAX_CONTEXT_LENGTH);

            log.info("构建上下文完成，开始调用LLM...");

            String response = callLlmWithRetry(context, 3);

            postprocessResponse(message, response);

            updateHistory(message, response);

            stats.incrementCommandsExecuted();

            setStatus(AgentStatus.INITIALIZED);

            return new AgentResult(response, true);

        } catch (Exception e) {
            log.error("处理消息失败", e);
            setStatus(AgentStatus.ERROR);
            return new AgentResult(e.getMessage(), false);
        }
    }

    private String callLlmWithRetry(String context, int maxRetries) {
        Exception lastException = null;
        for (int i = 0; i < maxRetries; i++) {
            try {
                return chatClient.prompt()
                    .system(context)
                    .call()
                    .content();
            } catch (Exception e) {
                lastException = e;
                log.warn("LLM调用失败，第{}次重试: {}", i + 1, e.getMessage());
                if (i < maxRetries - 1) {
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("LLM调用被中断", ie);
                    }
                }
            }
        }
        throw new RuntimeException("LLM调用失败，已重试" + maxRetries + "次", lastException);
    }

    private String determineMode(AgentTask task) {
        String description = task.getTaskContent().toLowerCase();

        int exploreScore = 0;
        int analyzeScore = 0;
        int planScore = 0;

        if (containsAny(description, "探索", "结构", "查看", "浏览", "目录", "文件列表", "了解")) {
            exploreScore += 2;
        }
        if (containsAny(description, "分析", "质量", "问题", "检查", "评估", "审查", "诊断", "bug", "错误")) {
            analyzeScore += 2;
        }
        if (containsAny(description, "计划", "下一步", "任务", "规划", "安排", "进度", "待办")) {
            planScore += 2;
        }

        if (containsAny(description, "代码结构", "项目结构")) {
            exploreScore += 1;
        }
        if (containsAny(description, "代码质量", "质量分析")) {
            analyzeScore += 1;
        }
        if (containsAny(description, "下一步计划", "任务计划")) {
            planScore += 1;
        }

        int maxScore = Math.max(exploreScore, Math.max(analyzeScore, planScore));
        if (maxScore == 0) {
            return "auto";
        }

        if (exploreScore == maxScore) return "explore";
        if (analyzeScore == maxScore) return "analyze";
        return "plan";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "\n...[已截断]";
    }

    private String buildSystemInstructions(String mode) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是 ").append(projectName != null ? projectName : "项目").append(" 的代码库维护助手。\n\n");
        sb.append("你的核心能力:\n");
        sb.append("1. 使用 Agent Skills 探索代码库\n");
        sb.append("2. 基于记忆系统提供连贯的建议\n\n");
        sb.append("当前会话ID: ").append(currentSessionId).append("\n");

        String modeInstructions = switch (mode) {
            case "explore" -> """
当前模式: 探索代码库

你应该:
- 主动使用 Skills 了解代码结构
- 识别关键模块和文件
- 记录项目架构到记忆
""";
            case "analyze" -> """
当前模式: 分析代码质量

你应该:
- 查找代码问题(重复、复杂度、TODO等)
- 评估代码质量
- 将发现的问题记录为重要记忆
""";
            case "plan" -> """
当前模式: 任务规划

你应该:
- 回顾历史记忆和任务
- 制定下一步行动计划
- 更新任务状态记忆
""";
            default -> """
当前模式: 自动决策

你应该:
- 根据用户需求灵活选择策略
- 在需要时使用工具
- 保持回答的专业性和实用性
""";
        };

        sb.append(modeInstructions);
        return sb.toString();
    }

    private void postprocessResponse(String userQuery, String response) {
        String responseLower = response.toLowerCase();

        if (responseLower.contains("问题") || responseLower.contains("bug") ||
            responseLower.contains("错误") || responseLower.contains("阻塞")) {
            addEpisodicMemory(currentSessionId, "Q: " + userQuery + "\nA: " + response, 0.7, "issue");
            SessionStats stats = sessionStatsMap.get(currentSessionId);
            if (stats != null) {
                stats.incrementIssuesFound();
            }
        }
    }

    private void updateHistory(String userQuery, String response) {
        addWorkingMemory(currentSessionId, "Q: " + truncate(userQuery, 100) + "\nA: " + truncate(response, 200), 0.5);
    }

    public String explore(String target) {
        AgentResult result = processMessage(currentSessionId, "请探索 " + target + " 的代码结构");
        return result.getMessage();
    }

    public String analyze(String focus) {
        String query = "请分析代码质量";
        if (focus != null && !focus.isEmpty()) {
            query += "，重点关注" + focus;
        }
        AgentResult result = processMessage(currentSessionId, query);
        return result.getMessage();
    }

    public String planNextSteps() {
        AgentResult result = processMessage(currentSessionId, "根据当前进度，规划下一步任务");
        return result.getMessage();
    }

    public String executeTerminalCommand(String command) {
        String result = "TerminalTool 已禁用，请使用 Agent Skills";

        SessionStats stats = sessionStatsMap.get(currentSessionId);
        if (stats != null) {
            stats.incrementCommandsExecuted();
        }

        return result;
    }

    public String createNote(String title, String content, String noteType, Double importance) {
        SessionStats stats = sessionStatsMap.get(currentSessionId);
        if (stats != null) {
            stats.incrementNotesCreated();
        }

        return "Note created: " + title;
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        stats.put("sessionInfo", Map.of(
            "sessionId", currentSessionId,
            "project", projectName,
            "codebasePath", codebasePath
        ));

        SessionStats sessionStats = sessionStatsMap.get(currentSessionId);
        if (sessionStats != null) {
            long duration = java.time.Duration.between(
                sessionStats.getSessionStart(), LocalDateTime.now()).getSeconds();

            stats.put("activity", Map.of(
                "commandsExecuted", sessionStats.getCommandsExecuted(),
                "notesCreated", sessionStats.getNotesCreated(),
                "issuesFound", sessionStats.getIssuesFound(),
                "durationSeconds", duration
            ));
        }

        stats.put("memoryStats", memoryManager.buildContextPrompt(currentSessionId, "").isEmpty() ?
            Map.of("hasMemory", false) : Map.of("hasMemory", true));

        return stats;
    }

    public Map<String, Object> generateReport() {
        Map<String, Object> report = getStats();
        report.put("generatedAt", LocalDateTime.now().toString());
        return report;
    }

    public void clearSession() {
        if (currentSessionId != null) {
            sessionStatsMap.remove(currentSessionId);
            memoryManager.clearWorkingMemory(currentSessionId);
        }
    }

    public void cleanupExpiredSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(MAX_SESSION_AGE_HOURS);
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, SessionStats> entry : sessionStatsMap.entrySet()) {
            SessionStats stats = entry.getValue();
            if (stats.getSessionStart() != null && stats.getSessionStart().isBefore(cutoff)) {
                toRemove.add(entry.getKey());
            }
        }

        for (String sessionId : toRemove) {
            sessionStatsMap.remove(sessionId);
            log.info("清理过期会话: {}", sessionId);
        }

        if (sessionStatsMap.size() > MAX_SESSIONS) {
            sessionStatsMap.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().getSessionStart(), Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(sessionStatsMap.size() - MAX_SESSIONS)
                .map(Map.Entry::getKey)
                .forEach(sessionId -> {
                    sessionStatsMap.remove(sessionId);
                    log.info("清理超量会话: {}", sessionId);
                });
        }
    }

    private static class SessionStats {
        private LocalDateTime sessionStart;
        private final AtomicInteger commandsExecuted = new AtomicInteger(0);
        private final AtomicInteger notesCreated = new AtomicInteger(0);
        private final AtomicInteger issuesFound = new AtomicInteger(0);

        public LocalDateTime getSessionStart() {
            return sessionStart;
        }

        public void setSessionStart(LocalDateTime sessionStart) {
            this.sessionStart = sessionStart;
        }

        public int getCommandsExecuted() {
            return commandsExecuted.get();
        }

        public void setCommandsExecuted(int commandsExecuted) {
            this.commandsExecuted.set(commandsExecuted);
        }

        public void incrementCommandsExecuted() {
            this.commandsExecuted.incrementAndGet();
        }

        public int getNotesCreated() {
            return notesCreated.get();
        }

        public void setNotesCreated(int notesCreated) {
            this.notesCreated.set(notesCreated);
        }

        public void incrementNotesCreated() {
            this.notesCreated.incrementAndGet();
        }

        public int getIssuesFound() {
            return issuesFound.get();
        }

        public void setIssuesFound(int issuesFound) {
            this.issuesFound.set(issuesFound);
        }

        public void incrementIssuesFound() {
            this.issuesFound.incrementAndGet();
        }
    }
}