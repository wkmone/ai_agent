# 数据库设计文档

## 1. 概述

### 1.1 数据库选型

- **PostgreSQL 15**: 主数据库，存储业务数据
- **Redis 7**: 缓存和会话存储
- **Neo4j 5.15**: 知识图谱存储
- **Milvus 2.3.3**: 向量数据存储

### 1.2 设计规范

- 使用下划线命名法（snake_case）
- 所有表必须有主键
- 使用逻辑删除（deleted 字段）
- 包含创建时间和更新时间

## 2. 核心表结构

### 2.1 model_config - 模型配置表

```sql
CREATE TABLE model_config (
    id BIGSERIAL PRIMARY KEY,
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称',
    base_url VARCHAR(500) COMMENT 'API 基础 URL',
    api_key VARCHAR(500) COMMENT 'API 密钥',
    temperature DECIMAL(3,2) DEFAULT 0.7 COMMENT '温度参数',
    max_tokens INTEGER DEFAULT 2048 COMMENT '最大 token 数',
    enabled BOOLEAN DEFAULT true COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE model_config IS 'AI 模型配置表';
COMMENT ON COLUMN model_config.model_name IS '模型名称：qwen-flash, deepseek-chat 等';
COMMENT ON COLUMN model_config.base_url IS 'API 基础 URL，如 https://dashscope.aliyuncs.com';
COMMENT ON COLUMN model_config.temperature IS '生成温度：0-1，越高越随机';
```

### 2.2 agent_config - Agent 配置表

```sql
CREATE TABLE agent_config (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT 'Agent 名称',
    model_config_id BIGINT REFERENCES model_config(id) COMMENT '关联的模型配置',
    system_prompt TEXT COMMENT '系统提示词',
    description VARCHAR(500) COMMENT '描述',
    enabled BOOLEAN DEFAULT true COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0 COMMENT '逻辑删除标记'
);

CREATE INDEX idx_agent_enabled ON agent_config(enabled) WHERE enabled = true;
```

### 2.3 agent_tool - Agent 工具关联表

```sql
CREATE TABLE agent_tool (
    id BIGSERIAL PRIMARY KEY,
    agent_config_id BIGINT REFERENCES agent_config(id) COMMENT '关联的 Agent',
    tool_name VARCHAR(100) NOT NULL COMMENT '工具名称',
    tool_type VARCHAR(50) COMMENT '工具类型：local, mcp',
    mcp_server_id BIGINT COMMENT '关联的 MCP 服务器',
    enabled BOOLEAN DEFAULT true COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_tool (agent_config_id, tool_name)
);

COMMENT ON TABLE agent_tool IS 'Agent 工具关联表';
```

### 2.4 mcp_server - MCP 服务器配置表

```sql
CREATE TABLE mcp_server (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '服务器名称',
    server_type VARCHAR(50) NOT NULL COMMENT '服务器类型：stdio, streamable',
    config JSONB NOT NULL COMMENT '服务器配置',
    description VARCHAR(500) COMMENT '描述',
    enabled BOOLEAN DEFAULT true COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN mcp_server.config IS 'JSON 格式配置：
STDIO 类型：{"command": "npx", "args": ["-y", "@mcp/tool"]}
HTTP 类型：{"url": "http://localhost:8000/mcp"}';
```

### 2.5 rag_knowledge_base - RAG 知识库表

```sql
CREATE TABLE rag_knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '知识库名称',
    description TEXT COMMENT '描述',
    collection_name VARCHAR(100) COMMENT 'Milvus 集合名称',
    enabled BOOLEAN DEFAULT true COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rag_enabled ON rag_knowledge_base(enabled) WHERE enabled = true;
```

### 2.6 chat_memory - 聊天记忆表

```sql
CREATE TABLE chat_memory (
    id BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(100) NOT NULL COMMENT '会话 ID',
    message_type VARCHAR(50) NOT NULL COMMENT '消息类型：user, assistant',
    content TEXT NOT NULL COMMENT '消息内容',
    agent_id BIGINT COMMENT '关联的 Agent',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_conversation ON chat_memory(conversation_id);
CREATE INDEX idx_created ON chat_memory(created_at);

COMMENT ON TABLE chat_memory IS '聊天记忆表，存储对话历史';
```

## 3. 数据字典

### 3.1 枚举值

**server_type** (MCP 服务器类型):
- `stdio`: 标准输入输出协议
- `streamable`: 可流式 HTTP 协议

**tool_type** (工具类型):
- `local`: 本地工具
- `mcp`: MCP 工具

**message_type** (消息类型):
- `user`: 用户消息
- `assistant`: AI 响应

## 4. 数据流转

### 4.1 Agent 创建流程

```
1. 创建 model_config 记录
   ↓
2. 创建 agent_config 记录，关联 model_config_id
   ↓
3. 创建 agent_tool 记录，关联工具
   ↓
4. Agent 加载配置
```

### 4.2 聊天流程

```
1. 用户发送消息
   ↓
2. 保存到 chat_memory (user)
   ↓
3. Agent 处理消息
   ↓
4. 保存响应到 chat_memory (assistant)
   ↓
5. 返回给用户
```

## 5. 索引优化

### 5.1 常用查询索引

```sql
-- Agent 查询
CREATE INDEX idx_agent_config_enabled_deleted 
ON agent_config(enabled, deleted) 
WHERE enabled = true AND deleted = 0;

-- MCP 服务器查询
CREATE INDEX idx_mcp_server_enabled 
ON mcp_server(enabled) 
WHERE enabled = true;

-- 聊天记忆查询
CREATE INDEX idx_chat_memory_conversation_created 
ON chat_memory(conversation_id, created_at DESC);
```

## 6. 数据迁移

### 6.1 迁移脚本位置

```
backend/src/main/resources/db/migration/
├── V1__initial_schema.sql
├── V2__add_agent_tool.sql
└── V3__add_mcp_server.sql
```

### 6.2 示例迁移脚本

```sql
-- V1__initial_schema.sql
CREATE TABLE IF NOT EXISTS model_config (
    id BIGSERIAL PRIMARY KEY,
    model_name VARCHAR(100) NOT NULL,
    base_url VARCHAR(500),
    api_key VARCHAR(500),
    temperature DECIMAL(3,2) DEFAULT 0.7,
    max_tokens INTEGER DEFAULT 2048,
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS agent_config (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    model_config_id BIGINT REFERENCES model_config(id),
    system_prompt TEXT,
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);
```

## 7. 备份恢复

### 7.1 数据库备份

```bash
# 备份整个数据库
pg_dump -U postgres -h localhost ai_agent > backup.sql

# 只备份结构
pg_dump -U postgres -h localhost --schema-only ai_agent > schema.sql

# 只备份数据
pg_dump -U postgres -h localhost --data-only ai_agent > data.sql
```

### 7.2 数据库恢复

```bash
# 恢复数据库
psql -U postgres -h localhost ai_agent < backup.sql
```

---

**文档版本**: v1.0  
**最后更新**: 2026-03-23  
**维护者**: AI Agent Team
