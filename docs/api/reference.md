# REST API 接口文档

## 1. 概述

### 1.1 基本信息

- **Base URL**: `/api/v1`
- **协议**: HTTP/HTTPS
- **数据格式**: JSON
- **字符编码**: UTF-8

### 1.2 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1234567890
}
```

**错误响应**:
```json
{
  "code": 400,
  "message": "错误描述",
  "data": null,
  "timestamp": 1234567890
}
```

## 2. Agent 接口

### 2.1 获取 Agent 列表

**接口**: `GET /api/v1/agent/list`

**响应**:
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "name": "智能助手",
      "modelConfigId": 1,
      "systemPrompt": "你是一个有帮助的助手",
      "enabled": true,
      "createdAt": "2026-03-23T10:00:00"
    }
  ]
}
```

### 2.2 创建 Agent

**接口**: `POST /api/v1/agent/create`

**请求**:
```json
{
  "name": "智能助手",
  "modelConfigId": 1,
  "systemPrompt": "你是一个有帮助的助手",
  "description": "通用智能助手",
  "toolIds": [1, 2, 3]
}
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "name": "智能助手"
  }
}
```

### 2.3 更新 Agent

**接口**: `PUT /api/v1/agent/{id}`

**路径参数**:
- `id`: Agent ID

**请求**:
```json
{
  "name": "智能助手",
  "modelConfigId": 1,
  "systemPrompt": "更新后的提示词",
  "enabled": true
}
```

### 2.4 删除 Agent

**接口**: `DELETE /api/v1/agent/{id}`

**路径参数**:
- `id`: Agent ID

**响应**:
```json
{
  "code": 200,
  "message": "删除成功"
}
```

### 2.5 聊天接口

**接口**: `POST /api/v1/agent/{id}/chat`

**路径参数**:
- `id`: Agent ID

**请求**:
```json
{
  "message": "你好，请介绍一下你自己",
  "conversationId": "conv_123456"
}
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "message": "你好！我是 AI 助手...",
    "toolCalls": [],
    "knowledgeContext": ""
  }
}
```

## 3. MCP 服务器接口

### 3.1 获取 MCP 服务器列表

**接口**: `GET /api/v1/mcp/servers`

**响应**:
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "name": "必应搜索",
      "serverType": "stdio",
      "config": {
        "command": "npx",
        "args": ["-y", "mcp-server-bing-search"]
      },
      "enabled": true,
      "createdAt": "2026-03-23T10:00:00"
    }
  ]
}
```

### 3.2 创建 MCP 服务器

**接口**: `POST /api/v1/mcp/servers`

**请求**:
```json
{
  "name": "必应搜索",
  "serverType": "stdio",
  "config": {
    "command": "npx",
    "args": ["-y", "mcp-server-bing-search"]
  },
  "description": "中文网络搜索工具"
}
```

### 3.3 更新 MCP 服务器

**接口**: `PUT /api/v1/mcp/servers/{id}`

**请求**:
```json
{
  "name": "必应搜索",
  "serverType": "stdio",
  "config": {
    "command": "npx",
    "args": ["-y", "mcp-server-bing-search"]
  },
  "enabled": true
}
```

### 3.4 删除 MCP 服务器

**接口**: `DELETE /api/v1/mcp/servers/{id}`

### 3.5 重新连接 MCP 服务器

**接口**: `POST /api/v1/mcp/servers/{id}/reconnect`

**路径参数**:
- `id`: MCP 服务器 ID

**响应**:
```json
{
  "code": 200,
  "message": "正在重新连接..."
}
```

### 3.6 重新连接所有服务器

**接口**: `POST /api/v1/mcp/servers/reconnect-all`

**响应**:
```json
{
  "code": 200,
  "message": "所有 MCP 服务器正在重连"
}
```

### 3.7 获取服务器工具列表

**接口**: `GET /api/v1/mcp/servers/{id}/tools`

**响应**:
```json
{
  "code": 200,
  "data": [
    {
      "name": "search",
      "description": "搜索网络信息",
      "inputSchema": {
        "type": "object",
        "properties": {
          "query": {
            "type": "string",
            "description": "搜索关键词"
          }
        },
        "required": ["query"]
      }
    }
  ]
}
```

## 4. 模型配置接口

### 4.1 获取模型配置列表

**接口**: `GET /api/v1/model-config/list`

**响应**:
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "modelName": "qwen-flash",
      "baseUrl": "https://dashscope.aliyuncs.com",
      "temperature": 0.7,
      "maxTokens": 2048,
      "enabled": true
    }
  ]
}
```

### 4.2 创建模型配置

**接口**: `POST /api/v1/model-config/create`

**请求**:
```json
{
  "modelName": "qwen-flash",
  "baseUrl": "https://dashscope.aliyuncs.com",
  "apiKey": "sk-xxx",
  "temperature": 0.7,
  "maxTokens": 2048
}
```

### 4.3 更新模型配置

**接口**: `PUT /api/v1/model-config/{id}`

### 4.4 删除模型配置

**接口**: `DELETE /api/v1/model-config/{id}`

## 5. RAG 知识库接口

### 5.1 获取知识库列表

**接口**: `GET /api/v1/rag-knowledge-base/list`

**响应**:
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "name": "产品知识库",
      "description": "产品信息相关",
      "collectionName": "product_kb",
      "enabled": true
    }
  ]
}
```

### 5.2 创建知识库

**接口**: `POST /api/v1/rag-knowledge-base/create`

**请求**:
```json
{
  "name": "产品知识库",
  "description": "产品信息相关",
  "collectionName": "product_kb"
}
```

### 5.3 添加文档

**接口**: `POST /api/v1/rag-knowledge-base/{id}/documents`

**请求**:
```json
{
  "content": "文档内容",
  "metadata": {
    "source": "manual",
    "author": "admin"
  }
}
```

### 5.4 搜索文档

**接口**: `POST /api/v1/rag-knowledge-base/{id}/search`

**请求**:
```json
{
  "query": "产品功能",
  "topK": 5
}
```

**响应**:
```json
{
  "code": 200,
  "data": [
    {
      "content": "产品功能介绍...",
      "score": 0.95,
      "metadata": {}
    }
  ]
}
```

## 6. 错误码说明

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
| 503 | 服务不可用 |

## 7. 认证说明

### 7.1 API Key 认证

在请求头中添加：
```
Authorization: Bearer {api_key}
```

### 7.2 获取 API Key

联系系统管理员获取 API Key。

## 8. 限流说明

- 默认限流：100 请求/分钟
- 聊天接口：20 请求/分钟
- 如需提升限流，请联系管理员

---

**文档版本**: v1.0  
**最后更新**: 2026-03-23  
**维护者**: AI Agent Team
