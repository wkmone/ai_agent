package com.wk.agent.impl;

import com.wk.agent.core.AbstractAgent;
import com.wk.agent.core.AgentResult;
import com.wk.agent.core.AgentStatus;
import com.wk.agent.core.AgentTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReflectionAgent extends AbstractAgent {
    private List<MemoryRecord> memoryRecords;
    private int maxIterations = 3;
    
    private String initialPromptTemplate;
    private String reflectPromptTemplate;
    private String refinePromptTemplate;
    
    private static final String DEFAULT_INITIAL_PROMPT = """
        你是一位资深的专家。请根据以下要求完成任务。
        你的输出必须完整、准确，并遵循最佳实践。
        
        要求: {task}
        
        请直接输出结果，不要包含任何额外的解释。
        """;
    
    private static final String DEFAULT_REFLECT_PROMPT = """
        你是一位极其严格的评审专家，对结果的质量有极致的要求。
        你的任务是审查以下结果，并专注于找出其主要问题和改进空间。
        
        # 原始任务:
        {task}
        
        # 待审查的结果:
        {result}
        
        请分析该结果的质量，并思考是否存在更好的解决方案。
        如果存在，请清晰地指出当前的不足，并提出具体的、可行的改进建议。
        如果结果已经达到最优，才能回答"无需改进"。
        
        请直接输出你的反馈，不要包含任何额外的解释。
        """;
    
    private static final String DEFAULT_REFINE_PROMPT = """
        你是一位资深的专家。你正在根据一位评审专家的反馈来优化你的结果。
        
        # 原始任务:
        {task}
        
        # 你上一轮尝试的结果:
        {last_result}
        
        评审员的反馈：
        {feedback}
        
        请根据评审员的反馈，生成一个优化后的新版本。
        请直接输出优化后的结果，不要包含任何额外的解释。
        """;
    
    private static class MemoryRecord {
        String type;
        String content;
        
        MemoryRecord(String type, String content) {
            this.type = type;
            this.content = content;
        }
    }
    
    public ReflectionAgent(String id, String name, String description) {
        super(id, name, description);
        this.memoryRecords = new ArrayList<>();
        this.initialPromptTemplate = DEFAULT_INITIAL_PROMPT;
        this.reflectPromptTemplate = DEFAULT_REFLECT_PROMPT;
        this.refinePromptTemplate = DEFAULT_REFINE_PROMPT;
    }
    
    public void setCustomPrompts(String initialPrompt, String reflectPrompt, String refinePrompt) {
        if (initialPrompt != null) {
            this.initialPromptTemplate = initialPrompt;
        }
        if (reflectPrompt != null) {
            this.reflectPromptTemplate = reflectPrompt;
        }
        if (refinePrompt != null) {
            this.refinePromptTemplate = refinePrompt;
        }
    }
    
    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }
    
    @Override
    public void initialize() {
        log.info("初始化ReflectionAgent: {} ({})", name, id);
        setStatus(AgentStatus.RUNNING);
        log.info("ReflectionAgent初始化完成: {} ({})", name, id);
    }
    
    @Override
    public AgentResult execute(AgentTask task) {
        log.info("执行ReflectionAgent任务: {} ({})", name, id);
        log.info("任务内容: {}", task.getTaskContent());
        log.info("最大迭代次数: {}", maxIterations);
        
        if (chatClient == null) {
            log.error("ReflectionAgent聊天客户端未初始化: {} ({})", name, id);
            return new AgentResult("聊天客户端未初始化", false);
        }
        
        try {
            resetMemory();
            String taskContent = task.getTaskContent();
            
            log.info("1. 初始执行");
            String initialResult = executeInitial(taskContent);
            addRecord("execution", initialResult);
            log.info("初始结果: {}", initialResult.substring(0, Math.min(100, initialResult.length())) + "...");
            
            log.info("2. 开始迭代循环: 反思与优化");
            String finalResult = initialResult;
            boolean converged = false;
            
            for (int i = 0; i < maxIterations && !converged; i++) {
                log.info("第 {}/{} 轮迭代", i + 1, maxIterations);
                
                String lastResult = getLastExecution();
                
                log.info("  a. 反思");
                String feedback = reflect(taskContent, lastResult);
                addRecord("reflection", feedback);
                log.info("  反馈: {}", feedback.substring(0, Math.min(100, feedback.length())) + "...");
                
                if (feedback.contains("无需改进")) {
                    log.info("  反思认为结果已无需改进，任务完成");
                    converged = true;
                    break;
                }
                
                log.info("  b. 优化");
                String refinedResult = refine(taskContent, lastResult, feedback);
                addRecord("execution", refinedResult);
                log.info("  优化结果: {}", refinedResult.substring(0, Math.min(100, refinedResult.length())) + "...");
                
                finalResult = refinedResult;
            }
            
            if (!converged) {
                log.info("达到最大迭代次数，任务完成");
            }
            
            AgentResult result = new AgentResult(finalResult, true);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("task", taskContent);
            metadata.put("trajectory", getTrajectory());
            metadata.put("iterations", memoryRecords.size());
            metadata.put("converged", converged);
            result.setMetadata(metadata);
            
            return result;
        } catch (Exception e) {
            log.error("执行反思任务时出错: {}", e.getMessage(), e);
            return new AgentResult("执行反思任务时出错: " + e.getMessage(), false);
        }
    }
    
    @Override
    public AgentResult processMessage(String sessionId, String message) {
        log.info("处理ReflectionAgent消息: sessionId={}, message={}", sessionId, message);

        AgentTask task = new AgentTask("reflection", message);
        return execute(task);
    }
    
    private String executeInitial(String task) {
        try {
            log.info("调用聊天客户端进行初始执行");
            String prompt = initialPromptTemplate.replace("{task}", task);
            
            org.springframework.ai.openai.OpenAiChatOptions.Builder optionsBuilder = org.springframework.ai.openai.OpenAiChatOptions.builder();
            if (modelName != null) {
                optionsBuilder.model(modelName);
                log.info("使用模型: {}", modelName);
            }
            if (temperature != null) {
                optionsBuilder.temperature(temperature);
                log.info("使用温度参数: {}", temperature);
            }
            
            String response = chatClient.prompt()
                .user(prompt)
                .options(optionsBuilder.build())
                .call()
                .content();
            
            log.info("初始执行成功");
            return response;
        } catch (Exception e) {
            log.error("初始执行时出错: {}", e.getMessage(), e);
            return "初始执行时出错：" + e.getMessage();
        }
    }
    
    private String reflect(String task, String result) {
        try {
            log.info("调用聊天客户端进行反思");
            String prompt = reflectPromptTemplate
                .replace("{task}", task)
                .replace("{result}", result);
            
            org.springframework.ai.openai.OpenAiChatOptions.Builder optionsBuilder = org.springframework.ai.openai.OpenAiChatOptions.builder();
            if (modelName != null) {
                optionsBuilder.model(modelName);
                log.info("使用模型: {}", modelName);
            }
            if (temperature != null) {
                optionsBuilder.temperature(temperature);
                log.info("使用温度参数: {}", temperature);
            }
            
            String response = chatClient.prompt()
                .user(prompt)
                .options(optionsBuilder.build())
                .call()
                .content();
            
            log.info("反思成功");
            return response;
        } catch (Exception e) {
            log.error("反思时出错: {}", e.getMessage(), e);
            return "反思时出错：" + e.getMessage();
        }
    }
    
    private String refine(String task, String lastResult, String feedback) {
        try {
            log.info("调用聊天客户端进行优化");
            String prompt = refinePromptTemplate
                .replace("{task}", task)
                .replace("{last_result}", lastResult)
                .replace("{feedback}", feedback);
            
            org.springframework.ai.openai.OpenAiChatOptions.Builder optionsBuilder = org.springframework.ai.openai.OpenAiChatOptions.builder();
            if (modelName != null) {
                optionsBuilder.model(modelName);
                log.info("使用模型: {}", modelName);
            }
            if (temperature != null) {
                optionsBuilder.temperature(temperature);
                log.info("使用温度参数: {}", temperature);
            }
            
            String response = chatClient.prompt()
                .user(prompt)
                .options(optionsBuilder.build())
                .call()
                .content();
            
            log.info("优化成功");
            return response;
        } catch (Exception e) {
            log.error("优化时出错: {}", e.getMessage(), e);
            return "优化时出错：" + e.getMessage();
        }
    }
    
    private void addRecord(String type, String content) {
        memoryRecords.add(new MemoryRecord(type, content));
        log.info("记忆已更新，新增一条 '{}' 记录", type);
    }
    
    private String getLastExecution() {
        for (int i = memoryRecords.size() - 1; i >= 0; i--) {
            if ("execution".equals(memoryRecords.get(i).type)) {
                return memoryRecords.get(i).content;
            }
        }
        return null;
    }
    
    private String getTrajectory() {
        StringBuilder trajectory = new StringBuilder();
        for (MemoryRecord record : memoryRecords) {
            if ("execution".equals(record.type)) {
                trajectory.append("--- 上一轮尝试 ---\n").append(record.content).append("\n\n");
            } else if ("reflection".equals(record.type)) {
                trajectory.append("--- 评审员反馈 ---\n").append(record.content).append("\n\n");
            }
        }
        return trajectory.toString();
    }
    
    private void resetMemory() {
        log.info("重置记忆");
        memoryRecords.clear();
        log.info("记忆重置成功");
    }
    
    public List<String> getReflections() {
        List<String> reflections = new ArrayList<>();
        for (MemoryRecord record : memoryRecords) {
            if ("reflection".equals(record.type)) {
                reflections.add(record.content);
            }
        }
        return reflections;
    }
    
    public List<String> getExecutions() {
        List<String> executions = new ArrayList<>();
        for (MemoryRecord record : memoryRecords) {
            if ("execution".equals(record.type)) {
                executions.add(record.content);
            }
        }
        return executions;
    }
}
