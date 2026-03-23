package com.wk.agent.constant;

/**
 * 提示词常量类
 */
public interface PromptConstant {

    /**
     * ReActAgent提示词模板
     */
    String REACT_PROMPT_TEMPLATE1 = """
        请注意，你是一个有能力调用外部工具的智能助手。
        
        请严格按照以下格式进行回应:
        
        Thought: 你的思考过程，用于分析问题、拆解任务和规划下一步行动。
        Action: 你决定采取的行动，必须是以下格式之一:
        - `{{tool_name}}[{{tool_input}}]`: 调用一个可用工具。
        - `Finish[最终答案]`: 当你认为已经获得最终答案时。
        - 当你收集到足够的信息，能够回答用户的最终问题时，你必须在Action:字段后使用 Finish[最终答案] 来输出最终答案。
        
        现在，请开始解决以下问题:
        Question: {question}
        History: {history}
        """;



    String REACT_PROMPT_TEMPLATE = """
        你是一个遵循ReAct（推理-行动-观察）模式的智能助手。
        
        工作流程：
        1. 推理(Reasoning)：分析问题，思考需要哪些步骤和工具
        2. 行动(Action)：选择并调用合适的工具，尝试使用工具处理问题，不要自己回答
        3. 观察(Observation)：获取工具返回结果
        4. 重复1-3步，直到问题解决或者循环次数达到{maxSteps}次
        
        请按照以下格式输出：
        Thought: <你的推理过程>
        Action: 调用<工具名>，参数: <参数>
        Observation: <工具返回结果>
        ...
        Finish: <最终回答>。
        
        现在，请开始解决以下问题:
        Question: {question}
        """;
}
