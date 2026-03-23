# 重写ReActAgent提示词计划

## 问题分析
当前ReActAgent的提示词存在以下问题：
- 提示词结构不够清晰，缺乏系统性
- 没有充分利用Spring AI的Message角色体系
- 提示词内容不够集中，目标不明确
- 没有明确工具调用的格式和要求

## 优化方案

### 1. 重构提示词结构
- 使用Spring AI的Message角色体系（System、User、Assistant、Tool）
- 为每个方法设计清晰的系统提示和用户提示
- 确保提示词目标明确，内容集中

### 2. 优化generateReasoning方法
- 设计简洁的系统提示，明确推理目标
- 清晰呈现任务和历史记录
- 强调推理的逻辑性和连贯性

### 3. 优化generateAction方法
- 明确列出可用工具及其功能
- 规范工具调用的格式和要求
- 强调行动与推理的直接关联性

### 4. 优化executeAction方法
- 明确工具执行的目标和要求
- 规范观察结果的格式和内容
- 确保工具调用的正确执行

### 5. 优化isTaskCompleted方法
- 明确任务完成的判断标准
- 简化判断逻辑，提高准确性

## 实现步骤
1. 分析当前ReActAgent的提示词实现
2. 设计新的提示词结构和内容
3. 重写generateReasoning、generateAction、executeAction和isTaskCompleted方法
4. 测试优化后的提示词效果

## 预期效果
- 提示词更加集中和结构化
- 工具调用决策更加准确
- ReActAgent的推理和行动更加高效
- 任务完成判断更加准确