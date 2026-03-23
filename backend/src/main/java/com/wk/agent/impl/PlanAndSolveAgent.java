package com.wk.agent.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wk.agent.core.AbstractAgent;
import com.wk.agent.core.AgentResult;
import com.wk.agent.core.AgentStatus;
import com.wk.agent.core.AgentTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlanAndSolveAgent extends AbstractAgent {
    private List<String> plans;
    private List<String> executionResults;
    private int maxPlanSteps = 10;
    
    private String plannerPrompt;
    private String executorPrompt;
    private boolean useSimpleMode = false;
    
    private static final String DEFAULT_PLANNER_PROMPT = """
        你是一个顶级的AI规划专家。你的任务是将用户提出的复杂问题分解成一个由多个简单步骤组成的行动计划。
        请确保计划中的每个步骤都是一个独立的、可执行的子任务，并且严格按照逻辑顺序排列。
        你的输出必须是一个JSON数组，其中每个元素都是一个描述子任务的字符串。
        
        问题: {question}
        
        请严格按照以下格式输出你的计划:
        ```json
        ["步骤1", "步骤2", "步骤3", ...]
        ```
        """;
    
    private static final String DEFAULT_EXECUTOR_PROMPT = """
        你是一位顶级的AI执行专家。你的任务是严格按照给定的计划，一步步地解决问题。
        你将收到原始问题、完整的计划、以及到目前为止已经完成的步骤和结果。
        请你专注于解决"当前步骤"，并仅输出该步骤的最终答案，不要输出任何额外的解释或对话。
        
        # 原始问题:
        {question}
        
        # 完整计划:
        {plan}
        
        # 历史步骤与结果:
        {history}
        
        # 当前步骤:
        {current_step}
        
        请仅输出针对"当前步骤"的回答:
        """;
    
    public PlanAndSolveAgent(String id, String name, String description) {
        super(id, name, description);
        this.plans = new ArrayList<>();
        this.executionResults = new ArrayList<>();
        this.plannerPrompt = DEFAULT_PLANNER_PROMPT;
        this.executorPrompt = DEFAULT_EXECUTOR_PROMPT;
    }
    
    public void setCustomPrompts(String plannerPrompt, String executorPrompt) {
        if (plannerPrompt != null) {
            this.plannerPrompt = plannerPrompt;
        }
        if (executorPrompt != null) {
            this.executorPrompt = executorPrompt;
        }
    }
    
    public void setUseSimpleMode(boolean useSimpleMode) {
        this.useSimpleMode = useSimpleMode;
    }
    
    public void setMaxPlanSteps(int maxPlanSteps) {
        this.maxPlanSteps = maxPlanSteps;
    }
    
    @Override
    public void initialize() {
        log.info("初始化PlanAndSolveAgent: {} ({})", name, id);
        setStatus(AgentStatus.RUNNING);
        log.info("PlanAndSolveAgent初始化完成: {} ({})", name, id);
    }
    
    @Override
    public AgentResult execute(AgentTask task) {
        log.info("执行PlanAndSolveAgent任务: {} ({})", name, id);
        log.info("任务内容: {}", task.getTaskContent());
        log.info("使用模式: {}", useSimpleMode ? "简单模式" : "完整模式");
        
        if (chatClient == null) {
            log.error("PlanAndSolveAgent聊天客户端未初始化: {} ({})", name, id);
            return new AgentResult("聊天客户端未初始化", false);
        }
        
        try {
            resetHistory();
            
            String problem = task.getTaskContent();
            
            if (useSimpleMode) {
                log.info("使用简单模式解决问题");
                return solveProblemDirectly(problem);
            }
            
            log.info("1. 生成计划");
            List<String> plan = generatePlan(problem);
            if (plan == null || plan.isEmpty()) {
                log.warn("计划生成失败，使用简化方法");
                return solveProblemDirectly(problem);
            }
            
            plans.addAll(plan);
            log.info("生成的计划: {}", plan);
            
            log.info("2. 执行计划");
            String finalResult = executePlan(plan, problem);
            log.info("计划执行成功，结果长度: {}", finalResult.length());
            
            AgentResult result = new AgentResult(finalResult, true);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("problem", problem);
            metadata.put("plan", plan);
            metadata.put("trajectory", getExecutionTrajectory(plan, problem));
            metadata.put("stepsExecuted", executionResults.size());
            result.setMetadata(metadata);
            
            return result;
        } catch (Exception e) {
            log.error("执行计划和解决任务时出错: {}", e.getMessage(), e);
            return new AgentResult("执行计划和解决任务时出错: " + e.getMessage(), false);
        }
    }
    
    @Override
    public AgentResult processMessage(String sessionId, String message) {
        log.info("处理PlanAndSolveAgent消息: sessionId={}, message={}", sessionId, message);

        AgentTask task = new AgentTask("plan_and_solve", message);
        return execute(task);
    }
    
    private List<String> generatePlan(String problem) {
        try {
            log.info("调用规划器生成计划");
            String prompt = plannerPrompt.replace("{question}", problem);
            
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
            
            log.info("规划器响应: {}", response);
            
            List<String> plan = parsePlanResponse(response);
            log.info("计划解析成功: {}", plan);
            return plan;
        } catch (Exception e) {
            log.error("生成计划时出错: {}", e.getMessage(), e);
            return null;
        }
    }
    
    private List<String> parsePlanResponse(String response) {
        List<String> plan = new ArrayList<>();
        
        try {
            String jsonContent = extractJsonContent(response);
            if (jsonContent != null) {
                ObjectMapper mapper = new ObjectMapper();
                plan = mapper.readValue(jsonContent, new TypeReference<List<String>>() {});
                log.info("使用JSON解析成功");
            }
        } catch (Exception e) {
            log.warn("JSON解析失败，尝试使用文本解析: {}", e.getMessage());
            plan = parsePlanText(response);
        }
        
        return plan;
    }
    
    private String extractJsonContent(String text) {
        Pattern pattern = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```");
        Matcher matcher = pattern.matcher(text);
        
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        int firstBracket = text.indexOf('[');
        int lastBracket = text.lastIndexOf(']');
        if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket) {
            return text.substring(firstBracket, lastBracket + 1);
        }
        
        return null;
    }
    
    private List<String> parsePlanText(String text) {
        List<String> plan = new ArrayList<>();
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.matches("^\\d+[.、)\\]].+")) {
                plan.add(line.replaceFirst("^\\d+[.、)\\]]\\s*", ""));
            } else if (line.matches("^-\\s+.+")) {
                plan.add(line.replaceFirst("^-\\s+", ""));
            }
        }
        return plan;
    }
    
    private String executePlan(List<String> plan, String problem) {
        log.info("执行计划，共 {} 个步骤", plan.size());
        
        for (int i = 0; i < Math.min(plan.size(), maxPlanSteps); i++) {
            String step = plan.get(i);
            log.info("执行步骤 {}: {}", i + 1, step);
            
            try {
                String executionResult = executeStep(step, problem, plan, executionResults);
                executionResults.add(executionResult);
                log.info("步骤 {} 执行成功", i + 1);
            } catch (Exception e) {
                log.error("步骤 {} 执行失败: {}", i + 1, e.getMessage());
                String errorResult = "执行失败: " + e.getMessage();
                executionResults.add(errorResult);
            }
        }
        
        log.info("生成综合最终结果");
        String finalResult = generateFinalResult(problem, plan);
        log.info("最终结果生成成功，长度: {}", finalResult.length());
        
        return finalResult;
    }
    
    private String generateFinalResult(String problem, List<String> plan) {
        try {
            StringBuilder historyBuilder = new StringBuilder();
            for (int i = 0; i < executionResults.size(); i++) {
                historyBuilder.append("### 步骤 ").append(i + 1).append(": ").append(plan.get(i)).append("\n");
                historyBuilder.append(executionResults.get(i)).append("\n\n");
            }
            
            String prompt = """
                你是一个顶级的AI总结专家。你的任务是根据以下执行步骤和结果，生成一个完整的、可直接使用的最终答案。
                
                # 原始问题:
                %s
                
                # 执行步骤和结果:
                %s
                
                请根据以上执行结果，生成一个完整的最终答案。要求：
                1. 如果是编程任务，请提供完整的、可运行的代码
                2. 如果是分析任务，请提供完整的分析报告
                3. 如果是其他任务，请提供完整、清晰的解决方案
                4. 不要重复执行过程中的中间结果，只给出最终的、完整的答案
                
                请直接输出最终答案:
                """.formatted(problem, historyBuilder.toString());
            
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
            
            return response;
        } catch (Exception e) {
            log.error("生成最终结果时出错: {}", e.getMessage(), e);
            if (!executionResults.isEmpty()) {
                return executionResults.get(executionResults.size() - 1);
            }
            return "生成最终结果时出错: " + e.getMessage();
        }
    }
    
    private String getExecutionTrajectory(List<String> plan, String problem) {
        StringBuilder trajectory = new StringBuilder();
        trajectory.append("问题: ").append(problem).append("\n\n");
        trajectory.append("计划:\n");
        for (int i = 0; i < plan.size(); i++) {
            trajectory.append((i + 1)).append(". ").append(plan.get(i)).append("\n");
        }
        trajectory.append("\n执行轨迹:\n");
        for (int i = 0; i < executionResults.size(); i++) {
            trajectory.append("步骤 ").append(i + 1).append(": ").append(plan.get(i)).append("\n");
            trajectory.append("结果: ").append(executionResults.get(i)).append("\n\n");
        }
        return trajectory.toString();
    }
    
    private String executeStep(String step, String problem, List<String> plan, List<String> history) {
        log.info("调用执行器执行步骤");
        
        String historyStr = "";
        for (int i = 0; i < history.size(); i++) {
            historyStr += "步骤" + (i + 1) + ": " + plan.get(i) + "\n";
            historyStr += "结果: " + history.get(i) + "\n\n";
        }
        
        String prompt = executorPrompt
            .replace("{question}", problem)
            .replace("{plan}", String.join("\n", plan))
            .replace("{history}", historyStr.isEmpty() ? "无" : historyStr)
            .replace("{current_step}", step);
        
        String response;
        if (temperature != null) {
            response = chatClient.prompt()
                .user(prompt)
                .options(org.springframework.ai.openai.OpenAiChatOptions.builder().temperature(temperature).build())
                .call()
                .content();
            log.info("使用温度参数: {}", temperature);
        } else {
            response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        }
        
        log.info("执行器响应: {}", response.substring(0, Math.min(100, response.length())) + "...");
        return response;
    }
    
    private AgentResult solveProblemDirectly(String problem) {
        try {
            log.info("直接解决问题");
            String prompt = "请帮我解决以下问题，给出一个完整的解决方案：\n" +
                    "问题：" + problem + "\n" +
                    "\n请提供清晰、可操作的解决方案。";
            
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
            
            log.info("问题解决成功");
            return new AgentResult(response, true);
        } catch (Exception e) {
            log.error("解决问题时出错: {}", e.getMessage(), e);
            return new AgentResult("解决问题时出错：" + e.getMessage(), false);
        }
    }
    
    private void resetHistory() {
        log.info("重置历史记录");
        plans.clear();
        executionResults.clear();
        log.info("历史记录重置成功");
    }
    
    public List<String> getPlans() {
        return plans;
    }
    
    public List<String> getExecutionResults() {
        return executionResults;
    }
}
