# AI Agent Docker 部署指南

## 📋 前置要求

- Docker 20.10+
- Docker Compose 2.0+
- 至少 8GB 可用内存
- 至少 20GB 可用磁盘空间

## 🛠️ 软件依赖

项目使用以下软件：

### 数据库
- **PostgreSQL 15** - 主数据库
- **Redis 7** - 缓存和短期记忆
- **Neo4j 5.15** - 知识图谱数据库
- **Milvus 2.3.3** - 向量数据库
- **RabbitMQ 3.12** - 消息队列

### 存储
- **MinIO** - 对象存储（Milvus 依赖）
- **Etcd 3.5.5** - 分布式键值存储（Milvus 依赖）

### 应用服务
- **Java 21** - 后端运行环境
- **Node.js 18** - 前端构建环境
- **Nginx** - 前端 Web 服务器

## 🚀 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/wkmone/ai_agent.git
cd ai_agent
```

### 2. 配置环境变量

```bash
# 复制环境变量配置文件
cp .env.example .env

# 编辑 .env 文件，配置必要的 API 密钥
# 特别是 OPENAI_API_KEY 和 BOCHA_API_KEY
```

### 3. 启动服务

#### 方式一：使用启动脚本（推荐）

```bash
# 添加执行权限
chmod +x start.sh

# 启动所有服务
./start.sh
```

#### 方式二：使用 Docker Compose 命令

```bash
# 构建镜像
docker-compose build

# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

## 🌐 服务访问

启动成功后，可以通过以下地址访问各个服务：

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端 | http://localhost | AI Agent Web 界面 |
| 后端 API | http://localhost:8080 | REST API 接口 |
| Milvus 管理 | http://localhost:3000 | 向量数据库管理界面 |
| RabbitMQ 管理 | http://localhost:15672 | 消息队列管理界面 |
| Neo4j 管理 | http://localhost:7474 | 图数据库管理界面 |
| MinIO 管理 | http://localhost:9001 | 对象存储管理界面 |

## 📝 常用命令

### 查看服务状态

```bash
docker-compose ps
```

### 查看日志

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f backend
docker-compose logs -f frontend
```

### 重启服务

```bash
# 重启所有服务
docker-compose restart

# 重启特定服务
docker-compose restart backend
```

### 停止服务

```bash
# 方式一：使用停止脚本
chmod +x stop.sh
./stop.sh

# 方式二：使用 Docker Compose
docker-compose down

# 删除数据卷（谨慎使用）
docker-compose down -v
```

### 更新服务

```bash
# 拉取最新代码
git pull

# 重新构建并启动
docker-compose build
docker-compose up -d
```

## 🔧 配置说明

### 环境变量

在 `.env` 文件中配置以下环境变量：

#### 数据库配置
- `DB_PASSWORD` - PostgreSQL 密码
- `REDIS_PASSWORD` - Redis 密码（可选）
- `NEO4J_PASSWORD` - Neo4j 密码
- `RABBITMQ_USER` / `RABBITMQ_PASSWORD` - RabbitMQ 用户名和密码
- `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` - MinIO 访问密钥

#### API 密钥配置
- `OPENAI_API_KEY` - OpenAI/阿里云 API 密钥（必需）
- `BOCHA_API_KEY` - 博查 API 密钥（可选）

### 数据持久化

所有数据都通过 Docker 卷进行持久化，数据存储在：

- `postgres_data` - PostgreSQL 数据
- `redis_data` - Redis 数据
- `neo4j_data` - Neo4j 数据
- `rabbitmq_data` - RabbitMQ 数据
- `etcd_data` - Etcd 数据
- `minio_data` - MinIO 数据
- `milvus_data` - Milvus 数据

### 日志文件

后端日志存储在 `./backend/logs` 目录。

## ⚠️ 注意事项

1. **首次启动时间较长**：Milvus 等服务的初始化需要较长时间，请耐心等待
2. **内存要求**：所有服务启动需要至少 8GB 内存，建议 16GB
3. **端口占用**：确保以下端口未被占用：80, 5432, 6379, 7474, 7687, 5672, 15672, 19530, 9000, 9001, 3000
4. **API 密钥**：必须配置有效的 API 密钥才能使用 AI 功能
5. **数据安全**：生产环境请修改默认密码

## 🐛 故障排查

### 后端启动失败

```bash
# 查看后端日志
docker-compose logs backend

# 检查数据库连接
docker-compose exec backend curl http://postgres:5432
```

### 前端无法访问

```bash
# 查看前端日志
docker-compose logs frontend

# 检查后端连接
docker-compose exec frontend curl http://backend:8080
```

### Milvus 启动失败

```bash
# 查看 Milvus 日志
docker-compose logs milvus

# 检查 Etcd 和 MinIO 状态
docker-compose ps etcd minio
```

### 内存不足

如果遇到内存不足错误，可以：

1. 调整 JVM 参数（在 `.env` 文件中）
2. 减少 Neo4j 内存配置
3. 关闭其他占用内存的应用

## 📊 资源监控

### 查看容器资源使用

```bash
docker stats
```

### 查看磁盘使用

```bash
docker system df
```

### 清理无用资源

```bash
# 清理停止的容器
docker container prune

# 清理悬空镜像
docker image prune

# 清理所有未使用资源（谨慎使用）
docker system prune -a
```

## 🆘 获取帮助

如有问题，请查看：

1. 项目文档
2. Docker 日志
3. GitHub Issues

## 📝 版本信息

- Docker Compose 版本：3.8
- PostgreSQL: 15-alpine
- Redis: 7-alpine
- Neo4j: 5.15-community
- RabbitMQ: 3.12-management-alpine
- Milvus: 2.3.3
- Java: 21
- Node.js: 18
