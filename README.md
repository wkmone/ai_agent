# AI Agent 智能助手系统

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Node.js](https://img.shields.io/badge/Node.js-18-green.svg)](https://nodejs.org/)
[![Vue](https://img.shields.io/badge/Vue-3.5-green.svg)](https://vuejs.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)

## 📖 项目简介

AI Agent 是一个功能强大的智能助手系统，基于 Spring AI 和 Vue 3 构建。系统集成了多种 AI 模型、MCP（Model Context Protocol）工具、RAG（检索增强生成）知识库等先进特性，能够为用户提供智能化的对话、问答、工具调用等服务。

### ✨ 核心特性

- 🤖 **智能对话** - 支持多轮对话，具备长期记忆和短期记忆能力
- 🔧 **工具集成** - 通过 MCP 协议集成各种外部工具（搜索、文件处理等）
- 📚 **知识库** - RAG 增强，支持向量检索和知识图谱
- 🌐 **多模型支持** - 支持阿里云、DeepSeek、OpenAI 等多种 AI 模型
- 💾 **持久化存储** - PostgreSQL + Redis + Neo4j + Milvus 多数据库支持
- 🐳 **容器化部署** - 完整的 Docker Compose 配置，一键部署

## 🏗️ 技术架构

```
┌─────────────┐
│   Frontend  │  Vue 3 + Vite + Element Plus
│   (Port 80) │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Backend   │  Spring Boot 4 + Spring AI 2.0
│ (Port 8080) │
└──────┬──────┘
       │
       ├──────────┬──────────┬──────────┬──────────┐
       ▼          ▼          ▼          ▼          ▼
┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
│PostgreSQL│ │  Redis   │ │  Neo4j   │ │  Milvus  │ │RabbitMQ  │
│  数据库  │ │  缓存    │ │ 知识图谱 │ │ 向量库  │ │ 消息队列 │
└──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘
```

## 🛠️ 技术栈

### 后端技术

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 | 编程语言 |
| Spring Boot | 4.0.3 | 应用框架 |
| Spring AI | 2.0.0-m2 | AI 集成框架 |
| MyBatis Plus | 3.5.5 | ORM 框架 |
| PostgreSQL | 15 | 主数据库 |
| Redis | 7 | 缓存和短期记忆 |
| Neo4j | 5.15 | 知识图谱数据库 |
| Milvus | 2.3.3 | 向量数据库 |
| RabbitMQ | 3.12 | 消息队列 |

### 前端技术

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5.30 | 渐进式框架 |
| Vite | 6.2.3 | 构建工具 |
| Element Plus | 2.9.10 | UI 组件库 |
| Pinia | 3.0.1 | 状态管理 |
| Axios | 1.8.4 | HTTP 客户端 |
| Vue Router | 4.5.0 | 路由管理 |

## 🚀 快速开始

### 环境要求

- Docker 20.10+
- Docker Compose 2.0+
- 至少 8GB 可用内存
- 至少 20GB 可用磁盘空间

### 一键部署

```bash
# 1. 克隆项目
git clone https://github.com/wkmone/ai_agent.git
cd ai_agent

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 文件，填入 API 密钥

# 3. 启动服务
chmod +x start.sh
./start.sh
```

### 访问服务

启动成功后，访问：

- **前端界面**: http://localhost
- **后端 API**: http://localhost:8080
- **API 文档**: http://localhost:8080/swagger-ui.html

## 📁 项目结构

```
ai_agent/
├── backend/                 # 后端服务
│   ├── src/main/java/      # Java 源代码
│   │   └── com/wk/agent/
│   │       ├── config/     # 配置类
│   │       ├── controller/ # 控制器
│   │       ├── service/    # 服务层
│   │       ├── entity/     # 实体类
│   │       ├── repository/ # 数据访问层
│   │       ├── core/       # 核心 Agent 实现
│   │       ├── mcp/        # MCP 客户端
│   │       └── impl/       # 实现类
│   ├── src/main/resources/ # 资源文件
│   │   ├── application.yml # 应用配置
│   │   └── db/migration/   # 数据库迁移脚本
│   ├── Dockerfile          # Docker 构建文件
│   └── pom.xml             # Maven 配置
├── frontend/               # 前端服务
│   ├── src/               # 源代码
│   │   ├── components/    # 组件
│   │   ├── views/         # 页面
│   │   ├── services/      # API 服务
│   │   ├── stores/        # 状态管理
│   │   └── router/        # 路由配置
│   ├── nginx.conf         # Nginx 配置
│   ├── Dockerfile         # Docker 构建文件
│   └── package.json       # Node 依赖
├── docs/                  # 文档目录
│   ├── architecture/      # 架构文档
│   ├── backend/           # 后端文档
│   ├── frontend/          # 前端文档
│   ├── mcp/              # MCP 文档
│   └── deployment/        # 部署文档
├── docker-compose.yml     # Docker 编排
├── .env.example          # 环境变量示例
├── start.sh              # 启动脚本
└── README.md             # 项目说明
```

## 🔌 MCP 工具集成

系统支持通过 MCP 协议集成各种工具：

### 内置工具

- 🔍 **必应搜索** - 中文网络搜索
- 🌐 **网页抓取** - 网页内容提取
- 📄 **文件处理** - 文件读写操作
- 🔧 **代码执行** - 代码运行环境

### 添加新工具

```yaml
# 在数据库中配置 MCP 服务器
{
  "name": "my-tool",
  "server_type": "stdio",
  "config": {
    "command": "npx",
    "args": ["-y", "@mcp/my-tool"]
  }
}
```

## 📊 数据库设计

### 核心表

- `model_config` - 模型配置表
- `agent_config` - Agent 配置表
- `agent_tool` - Agent 工具关联表
- `mcp_server` - MCP 服务器配置表
- `rag_knowledge_base` - RAG 知识库表
- `chat_memory` - 聊天记忆表

详细设计见：[docs/backend/database.md](docs/backend/database.md)

## 🔧 配置说明

### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `DB_PASSWORD` | PostgreSQL 密码 | 123456 |
| `REDIS_PASSWORD` | Redis 密码 | - |
| `NEO4J_PASSWORD` | Neo4j 密码 | memory123 |
| `OPENAI_API_KEY` | OpenAI API 密钥 | - |
| `BOCHA_API_KEY` | 博查 API 密钥 | - |

完整配置见：[.env.example](.env.example)

## 📖 文档导航

- [架构设计](docs/architecture/overview.md) - 系统架构设计文档
- [后端开发指南](docs/backend/development.md) - 后端开发详细指南
- [前端开发指南](docs/frontend/development.md) - 前端开发详细指南
- [MCP 工具集成](docs/mcp/integration.md) - MCP 工具集成指南
- [部署文档](docs/deployment/docker.md) - Docker 部署完整指南
- [API 文档](docs/api/reference.md) - REST API 接口文档

## 🔍 常见问题

### Q: 如何修改 AI 模型？

A: 在 `.env` 文件中修改 `OPENAI_API_KEY`，并在数据库的 `model_config` 表中配置模型参数。

### Q: 如何添加新的 MCP 工具？

A: 在 MCP 服务器管理界面中添加新的服务器配置，系统会自动发现并注册工具。

### Q: 服务启动失败怎么办？

A: 查看日志 `docker-compose logs backend`，检查端口占用和资源是否充足。

更多问题见：[docs/deployment/faq.md](docs/deployment/faq.md)

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 📄 开源协议

MIT License - 详见 [LICENSE](LICENSE) 文件

## 📞 联系方式

- 项目地址：https://github.com/wkmone/ai_agent
- Issue 反馈：https://github.com/wkmone/ai_agent/issues

## 🙏 致谢

感谢以下开源项目：

- [Spring AI](https://spring.io/projects/spring-ai)
- [Vue.js](https://vuejs.org/)
- [Element Plus](https://element-plus.org/)
- [Milvus](https://milvus.io/)
- [Neo4j](https://neo4j.com/)

---

**Made with ❤️ by AI Agent Team**
