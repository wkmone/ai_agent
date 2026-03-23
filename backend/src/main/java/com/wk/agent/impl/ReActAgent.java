package com.wk.agent.impl;

import com.wk.agent.core.AbstractAgent;
import com.wk.agent.core.AgentResult;
import com.wk.agent.core.AgentStatus;
import com.wk.agent.core.AgentTask;
import com.wk.agent.entity.SessionMemory;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;

public class ReActAgent extends AbstractAgent {

    @Resource
    private ToolCallback[] allTools;

    private static final int DEFAULT_MAX_ITERATIONS = 10;

    private int maxIterations;
    private String currentSessionId;

    public ReActAgent(String id, String name, String description) {
        super(id, name, description);
        this.maxIterations = DEFAULT_MAX_ITERATIONS;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    @Override
    public void initialize() {
        log.info("初始化ReActAgent: {} ({})", name, id);
        setStatus(AgentStatus.RUNNING);
        log.info("ReActAgent初始化完成: {} ({})", name, id);
    }

    @Override
    public AgentResult execute(AgentTask task) {
        log.info("执行ReActAgent任务: {} ({})", name, id);
        log.info("任务内容: {}", task.getTaskContent());

        if (chatClient == null) {
            log.error("ReActAgent聊天客户端未初始化: {} ({})", name, id);
            return new AgentResult("聊天客户端未初始化", false);
        }

        this.currentSessionId = task.getSessionId();

        try {
            String result = runReActLoop(task.getSessionId(), task.getTaskContent());
            log.info("ReAct循环完成，结果长度: {}", result.length());
            return new AgentResult(result, true);
        } catch (Exception e) {
            log.error("执行ReAct循环时出错: {}", e.getMessage(), e);
            return new AgentResult("执行任务时出错: " + e.getMessage(), false);
        }
    }

    @Override
    public AgentResult processMessage(String sessionId, String message) {
        log.info("处理ReActAgent消息: sessionId={}", sessionId);

        if (chatClient == null) {
            log.error("ReActAgent聊天客户端未初始化: {} ({})", name, id);
            return new AgentResult("聊天客户端未初始化", false);
        }

        this.currentSessionId = sessionId;

        try {
            String result = runReActLoop(sessionId, message);
            return new AgentResult(result, true);
        } catch (Exception e) {
            log.error("处理消息时出错: {}", e.getMessage(), e);
            return new AgentResult("处理消息时出错: " + e.getMessage(), false);
        }
    }

    private String runReActLoop(String sessionId, String userInput) {
        List<Message> conversationHistory = new ArrayList<>();

        String memoryContext = buildContext(sessionId, userInput);

        log.info("╔════════════════════════════════════════╗");
        log.info("║       🚀 ReAct Agent 启动              ║");
        log.info("╚════════════════════════════════════════╝");
        log.info("💬 问题：{}", userInput);
        if (!memoryContext.isEmpty()) {
            log.info("📝 已加载记忆上下文，长度：{}", memoryContext.length());
        }

        List<SessionMemory> workingMemories = getWorkingMemories(sessionId);
        if (!workingMemories.isEmpty()) {
            log.info("📚 工作记忆数量: {}", workingMemories.size());
        }

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            log.info("┌────────────────────────────────────────");
            log.info("│ 🔁 第 {} 轮思考", iteration);
            log.info("└────────────────────────────────────────");

            try {
                ChatResponse response = think(sessionId, userInput, conversationHistory);
                AssistantMessage assistantMessage = response.getResult().getOutput();

                if (hasToolCalls(assistantMessage)) {
                    String toolResult = executeTools(assistantMessage);

                    var toolCall = getToolCall(assistantMessage);
                    conversationHistory.add(ToolResponseMessage.builder().responses(
                            List.of(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), toolResult))
                    ).build());

                    log.info("工具执行结果 {} => {}", toolCall.name(), toolResult);

                } else {
                    String finalAnswer = assistantMessage.getText();
                    log.info(" ✅ 无工具调用，直接返回结果：{}", finalAnswer);

                    log.info("╔════════════════════════════════════════╗");
                    log.info("║       ✨ ReAct 任务完成 ✨              ║");
                    log.info("╚════════════════════════════════════════╝\n");
                    return finalAnswer;
                }

            } catch (Exception e) {
                log.error("ReAct 循环出错：{}", e.getMessage(), e);
                break;
            }
        }

        log.info("╔════════════════════════════════════════╗");
        log.info("║ ⚠️  达到最大迭代次数，任务终止          ║");
        log.info("╚════════════════════════════════════════╝\n");
        return "达到最大迭代次数 (" + maxIterations + ")，无法完成任务。";
    }

    private ChatResponse think(String sessionId, String userInput, List<Message> conversationHistory) {
        String memoryContext = buildContext(sessionId, userInput);

        String systemPrompt = """
                你是一个智能助手，使用 ReAct 范式解决问题。

                请按以下步骤思考：
                1. 分析当前问题和已有信息
                2. 判断是否需要使用工具获取更多信息
                3. 如果需要工具，调用一个合适的工具
                4. 【重要】如果已有足够信息，直接给出最终答案

                响应规则：
                - 你必须一次只调用一个工具，不允许同时调用多个工具
                - 关键：调用工具时，你必须使用工具定义中完整的工具名称
                - 【重要】如果之前的工具执行已经提供了足够的信息来回答原始问题，不要再调用另一个工具。相反，综合结果并直接提供你的最终答案

                """ + (memoryContext.isEmpty() ? "" : "\n" + memoryContext + "\n") + """

                可用工具列表：
                """ + getToolDescriptions();

        List<Message> messages = new ArrayList<>();
        messages.add(new org.springframework.ai.chat.messages.SystemMessage(systemPrompt));
        messages.addAll(conversationHistory);

        ToolCallingChatOptions.Builder optionsBuilder = ToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(false);
        
        if (modelName != null) {
            optionsBuilder.model(modelName);
            log.info("使用模型: {}", modelName);
        }
        if (temperature != null) {
            optionsBuilder.temperature(temperature);
            log.info("使用温度参数: {}", temperature);
        }

        Prompt prompt = new Prompt(messages, optionsBuilder.build());

        return chatClient.prompt(prompt)
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
    }

    private boolean hasToolCalls(AssistantMessage message) {
        return message.getToolCalls() != null && !message.getToolCalls().isEmpty();
    }

    private AssistantMessage.ToolCall getToolCall(AssistantMessage message) {
        if (hasToolCalls(message)) {
            return message.getToolCalls().get(0);
        }
        return null;
    }

    private String executeTools(AssistantMessage message) {
        var toolCall = getToolCall(message);

        log.debug("  ┌─ 🔧 执行工具调用");
        log.debug("  │   工具名称：{}", toolCall.name());
        log.debug("  │   工具参数：{}", toolCall.arguments());

        if (toolCallbacks != null) {
            for (ToolCallback tool : toolCallbacks) {
                if (tool.getToolDefinition().name().equals(toolCall.name())) {
                    Object result = tool.call(toolCall.arguments());
                    String resultText = result != null ? result.toString() : "null";
                    log.debug("  │   执行结果：{}", resultText);
                    log.debug("  └─ ✓ 工具执行完成\n");
                    return resultText;
                }
            }
        }

        return "错误：未找到工具 " + toolCall.name();
    }
}