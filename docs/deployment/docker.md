# Docker 部署完整指南

## 1. 概述

本文档详细介绍如何使用 Docker 部署 AI Agent 智能助手系统。

### 1.1 部署架构

```
┌─────────────────────────────────────────┐
│         Docker Compose                  │
│                                         │
│  ┌─────────────┐  ┌──────────────┐     │
│  │  Frontend   │  │   Backend    │     │
│  │  (Nginx)    │  │  (Spring)    │     │
│  │  Port 80    │  │  Port 8080   │     │
│  └─────────────┘  └──────────────┘     │
│                                         │
│  ┌─────────────┐  ┌──────────────┐     │
│  │ PostgreSQL  │  │    Redis     │     │
│  └─────────────┘  └──────────────┘     │
│                                         │
│  ┌─────────────┐  ┌──────────────┐     │
│  │   Neo4j     │  │  RabbitMQ    │     │
│  └─────────────┘  └──────────────┘     │
│                                         │
│  ┌─────────────┐  ┌──────────────┐     │
│  │   Milvus    │  │    MinIO     │     │
│  └─────────────┘  └──────────────┘     │
│                                         │
└─────────────────────────────────────────┘
```

### 1.2 服务清单

| 服务 | 端口 | 说明 |
|------|------|------|
| frontend | 80 | Web 前端 |
| backend | 8080 | Spring Boot 后端 |
| postgres | 5432 | PostgreSQL 数据库 |
| redis | 6379 | Redis 缓存 |
| neo4j | 7474, 7687 | Neo4j 图数据库 |
| rabbitmq | 5672, 15672 | RabbitMQ 消息队列 |
| milvus | 19530, 9091 | Milvus 向量数据库 |
| milvus-attu | 3000 | Milvus 管理界面 |
| minio | 9000, 9001 | MinIO 对象存储 |

## 2. 前置要求

### 2.1 系统要求

- **操作系统**: Linux / macOS / Windows with WSL2
- **CPU**: 至少 4 核，推荐 8 核
- **内存**: 至少 8GB，推荐 16GB
- **磁盘**: 至少 20GB 可用空间
- **Docker**: 20.10+
- **Docker Compose**: 2.0+

### 2.2 安装 Docker

#### Ubuntu/Debian

```bash
# 安装 Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# 启动 Docker
sudo systemctl start docker
sudo systemctl enable docker

# 添加用户到 docker 组
sudo usermod -aG docker $USER
```

#### macOS

```bash
# 下载并安装 Docker Desktop
# https://docs.docker.com/desktop/install/mac-install/
```

#### Windows

```bash
# 安装 Docker Desktop
# https://docs.docker.com/desktop/install/windows-install/
```

### 2.3 验证安装

```bash
# 检查 Docker 版本
docker --version

# 检查 Docker Compose 版本
docker-compose --version

# 运行测试容器
docker run hello-world
```

## 3. 快速部署

### 3.1 克隆项目

```bash
git clone https://github.com/wkmone/ai_agent.git
cd ai_agent
```

### 3.2 配置环境变量

```bash
# 复制环境变量示例文件
cp .env.example .env

# 编辑环境变量
vim .env
```

**必须配置的环境变量**:

```bash
# API 密钥（必须配置）
OPENAI_API_KEY=your_api_key_here
BOCHA_API_KEY=your_bocha_key_here

# 数据库密码（建议修改）
DB_PASSWORD=your_secure_password
NEO4J_PASSWORD=your_neo4j_password
```

### 3.3 启动服务

```bash
# 方式一：使用启动脚本（推荐）
chmod +x start.sh
./start.sh

# 方式二：使用 Docker Compose
docker-compose build
docker-compose up -d
```

### 3.4 验证部署

```bash
# 查看所有服务状态
docker-compose ps

# 查看后端日志
docker-compose logs backend

# 查看前端日志
docker-compose logs frontend

# 测试后端 API
curl http://localhost:8080/actuator/health

# 测试前端
curl http://localhost/
```

## 4. 服务访问

### 4.1 Web 界面

- **前端界面**: http://localhost
- **后端 API**: http://localhost:8080
- **API 文档**: http://localhost:8080/swagger-ui.html

### 4.2 管理界面

- **RabbitMQ 管理**: http://localhost:15672
  - 用户名：guest
  - 密码：guest

- **Neo4j 管理**: http://localhost:7474
  - 用户名：neo4j
  - 密码：配置的密码

- **Milvus 管理**: http://localhost:3000
  - 地址：milvus:19530

- **MinIO 管理**: http://localhost:9001
  - 用户名：minioadmin
  - 密码：minioadmin

## 5. 日常运维

### 5.1 启动停止

```bash
# 启动所有服务
docker-compose up -d

# 停止所有服务
docker-compose down

# 重启所有服务
docker-compose restart

# 重启特定服务
docker-compose restart backend

# 停止并删除数据卷（谨慎使用）
docker-compose down -v
```

### 5.2 查看日志

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f backend
docker-compose logs -f frontend

# 查看最近 100 行
docker-compose logs --tail=100 backend
```

### 5.3 进入容器

```bash
# 进入后端容器
docker-compose exec backend sh

# 进入前端容器
docker-compose exec frontend sh

# 进入数据库容器
docker-compose exec postgres psql -U postgres -d ai_agent
```

### 5.4 备份恢复

#### 数据库备份

```bash
# 备份 PostgreSQL
docker-compose exec postgres pg_dump -U postgres ai_agent > backup.sql

# 恢复 PostgreSQL
docker-compose exec -T postgres psql -U postgres ai_agent < backup.sql
```

#### Milvus 备份

```bash
# 备份 Milvus 数据
docker run --rm \
  -v ai_agent_milvus_data:/source \
  -v $(pwd):/backup \
  alpine tar czf /backup/milvus_backup.tar.gz -C /source .
```

## 6. 故障排查

### 6.1 服务启动失败

#### 问题：后端启动失败

```bash
# 查看后端日志
docker-compose logs backend

# 检查数据库连接
docker-compose exec backend curl http://postgres:5432

# 检查环境变量
docker-compose exec backend env | grep SPRING
```

**常见原因**:
1. 数据库未启动完成
2. 环境变量配置错误
3. 端口被占用
4. 内存不足

**解决方案**:
```bash
# 等待数据库启动
sleep 30

# 重启后端
docker-compose restart backend

# 检查端口占用
lsof -i :8080
```

#### 问题：前端无法访问

```bash
# 查看前端日志
docker-compose logs frontend

# 检查 Nginx 配置
docker-compose exec frontend nginx -t

# 测试后端连接
docker-compose exec frontend curl http://backend:8080
```

### 6.2 性能问题

#### 内存不足

```bash
# 查看容器资源使用
docker stats

# 调整 JVM 参数（在 .env 文件中）
JVM_XMS=256m
JVM_XMX=512m

# 调整 Neo4j 内存
NEO4J_dbms_memory_heap_max__size=512m
```

#### 磁盘空间不足

```bash
# 查看磁盘使用
docker system df

# 清理未使用资源
docker system prune -a

# 清理日志文件
sudo truncate -s 0 /var/lib/docker/containers/*/*-json.log
```

### 6.3 网络问题

#### 容器间无法通信

```bash
# 查看网络配置
docker network ls
docker network inspect ai_agent_ai_agent_network

# 重启网络
docker-compose down
docker network rm ai_agent_ai_agent_network
docker-compose up -d
```

## 7. 监控告警

### 7.1 健康检查

```bash
# 检查服务健康状态
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness
```

### 7.2 指标收集

后端暴露了 Prometheus 格式的指标：

```bash
# 访问指标端点
curl http://localhost:8080/actuator/prometheus
```

### 7.3 日志聚合

配置日志输出到文件：

```yaml
# 在 docker-compose.yml 中
backend:
  volumes:
    - ./logs:/app/logs
```

## 8. 升级更新

### 8.1 代码更新

```bash
# 拉取最新代码
git pull

# 重新构建镜像
docker-compose build

# 重启服务
docker-compose up -d
```

### 8.2 数据库迁移

```bash
# 查看迁移脚本
ls backend/src/main/resources/db/migration/

# 手动执行迁移（如果需要）
docker-compose exec postgres psql -U postgres -d ai_agent -f /docker-entrypoint-initdb.d/V001__init.sql
```

## 9. 安全加固

### 9.1 修改默认密码

```bash
# 在 .env 文件中修改
DB_PASSWORD=strong_password
REDIS_PASSWORD=strong_password
NEO4J_PASSWORD=strong_password
RABBITMQ_PASSWORD=strong_password
```

### 9.2 网络隔离

```yaml
# 在 docker-compose.yml 中
networks:
  ai_agent_network:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16
```

### 9.3 限制资源

```yaml
# 在 docker-compose.yml 中
backend:
  deploy:
    resources:
      limits:
        cpus: '2'
        memory: 2G
```

## 10. 生产环境部署

### 10.1 高可用配置

```yaml
# 多实例部署
backend:
  deploy:
    replicas: 3
    restart_policy:
      condition: on-failure
```

### 10.2 负载均衡

使用 Nginx 作为负载均衡器：

```nginx
upstream backend_servers {
    server backend1:8080;
    server backend2:8080;
    server backend3:8080;
}

server {
    location /api/ {
        proxy_pass http://backend_servers;
    }
}
```

### 10.3 数据持久化

使用外部存储：

```yaml
volumes:
  postgres_data:
    driver: local
    driver_opts:
      type: none
      device: /data/postgres
      o: bind
```

## 11. 常见问题 FAQ

### Q: 首次启动很慢怎么办？

A: Milvus 等服务首次启动需要初始化，可能需要 2-5 分钟，请耐心等待。可以查看日志了解进度。

### Q: 如何重置所有数据？

A: 
```bash
docker-compose down -v
docker-compose up -d
```

### Q: 如何查看数据库内容？

A:
```bash
docker-compose exec postgres psql -U postgres -d ai_agent
\dt  # 查看所有表
SELECT * FROM model_config;
```

### Q: 如何添加新的 MCP 工具？

A: 在 MCP 服务器管理界面中添加配置，或直接在数据库的 `mcp_server` 表中插入记录。

### Q: 服务占用内存过高？

A: 调整各服务的内存限制，特别是 Neo4j 和 JVM 参数。

---

**文档版本**: v1.0  
**最后更新**: 2026-03-23  
**维护者**: AI Agent Team
