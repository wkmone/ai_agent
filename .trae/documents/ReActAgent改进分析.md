# ReActAgent改进计划

## 分析Spring AI工具注册与提示词的关系

### 现状分析
- Spring AI的`ChatClient`通过`defaultToolCallbacks(allTools)`注册工具
- 这使得Spring AI能够自动执行工具调用
- 但模型本身仍需要知道有哪些工具可用及其参数格式

### 工具描述的必要性
1. **模型需要工具信息**：模型需要了解工具的功能和参数，才能生成正确的工具调用
2. **动态工具管理**：当添加或修改工具时，提示词应自动更新
3. **提高可维护性**：避免硬编码工具列表，减少手动维护

## 改进方案

### 1. 动态工具描述生成
- 创建`ToolDescriptionGenerator`类，从`ToolProvider`获取工具信息
- 解析`@Tool`和`@ToolParam`注解，生成标准化的工具描述
- 在`PromptConstant`中修改提示词模板，使用`{tools}`占位符

### 2. 统一历史记录管理
- 合并`reasoningHistory`、`actionHistory`和`observationHistory`为一个统一的`history`列表
- 标准化历史记录格式：`Action: {action}`和`Observation: {observation}`
- 简化历史记录的构建和管理

### 3. 封装解析方法
- 创建专门的解析方法，如`_parse_output`、`_parse_action`
- 提高代码的可读性和可维护性
- 统一处理响应解析逻辑

### 4. 增强错误处理
- 添加更详细的错误处理逻辑
- 处理工具调用失败的情况
- 提供更友好的错误提示

## 实现步骤
1. 创建`ToolDescriptionGenerator`类，实现动态工具描述生成
2. 修改`PromptConstant`中的提示词模板，使用`{tools}`占位符
3. 修改`ReActAgent`的历史记录管理
4. 封装解析方法
5. 增强错误处理
6. 测试验证

## 预期效果
- 代码更加模块化和可维护
- 工具描述自动更新，无需手动修改
- 与Python实现的功能更加接近
- 充分利用Spring AI的自动工具调用特性