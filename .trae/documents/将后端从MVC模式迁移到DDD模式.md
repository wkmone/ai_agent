# 将后端从MVC模式迁移到DDD模式

## 1. 项目结构调整

### 1.1 新建DDD目录结构
- `domain/` - 领域层
  - `model/` - 领域模型
  - `service/` - 领域服务
  - `repository/` - 仓储接口
  - `event/` - 领域事件
- `application/` - 应用层
  - `service/` - 应用服务
  - `dto/` - 数据传输对象
- `infrastructure/` - 基础设施层
  - `repository/` - 仓储实现
  - `config/` - 配置
  - `mapper/` - MyBatis映射器

### 1.2 保留现有目录
- `controller/` - 控制器层
- `exception/` - 异常处理
- `rabbitmq/` - 消息队列

## 2. 领域模型设计

### 2.1 聚合根
- `Agent` - 智能体聚合根
- `Conversation` - 对话聚合根

### 2.2 实体
- `AgentTask` - 智能体任务
- `Message` - 消息
- `ModelConfig` - 模型配置

### 2.3 值对象
- `AgentId` - 智能体ID
- `AgentResult` - 智能体执行结果
- `AgentStatus` - 智能体状态

## 3. 核心组件实现

### 3.1 领域服务
- `AgentDomainService` - 智能体领域服务
- `ConversationDomainService` - 对话领域服务
- `TaskDomainService` - 任务领域服务

### 3.2 仓储接口
- `AgentRepository` - 智能体仓储
- `ConversationRepository` - 对话仓储
- `MessageRepository` - 消息仓储
- `ModelConfigRepository` - 模型配置仓储

### 3.3 应用服务
- `AgentApplicationService` - 智能体应用服务
- `ConversationApplicationService` - 对话应用服务
- `TaskApplicationService` - 任务应用服务

## 4. 迁移步骤

### 4.1 第一步：创建领域模型
- 将现有的核心类迁移到domain/model
- 定义聚合根和实体关系
- 实现值对象

### 4.2 第二步：实现领域服务
- 从现有的service/impl中提取业务逻辑
- 封装领域规则到领域服务
- 实现领域事件

### 4.3 第三步：实现仓储
- 定义仓储接口
- 基于现有mapper实现仓储
- 处理持久化逻辑

### 4.4 第四步：实现应用服务
- 协调领域对象完成用例
- 处理事务和错误
- 转换DTO

### 4.5 第五步：调整控制器
- 从调用service改为调用application service
- 处理HTTP请求和响应
- 保持API兼容性

### 4.6 第六步：测试和验证
- 单元测试
- 集成测试
- 功能验证

## 5. 关键修改点

### 5.1 Agent相关
- 将AbstractAgent及其实现迁移到domain/model
- 实现AgentFactory作为领域服务
- 重构AgentService为应用服务

### 5.2 数据访问
- 从直接使用mapper改为通过repository
- 实现仓储接口和实现
- 保持数据结构兼容性

### 5.3 业务逻辑
- 将业务规则从service迁移到domain service
- 实现领域事件处理
- 确保业务逻辑的一致性

## 6. 预期收益

- 更好的领域模型表达
- 业务逻辑的集中管理
- 更清晰的代码结构
- 更好的可测试性
- 更容易应对业务变化

## 7. 风险评估

- 迁移过程中的兼容性风险
- 重构可能引入的bug
- 学习成本和时间投入

## 8. 实施策略

- 采用增量迁移方式
- 先迁移核心功能
- 保持API兼容性
- 充分测试确保功能正常