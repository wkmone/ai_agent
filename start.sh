#!/bin/bash

# AI Agent 项目 Docker 启动脚本

set -e

echo "========================================"
echo "  AI Agent - Docker 启动脚本"
echo "========================================"
echo ""

# 检查 Docker 是否安装
if ! command -v docker &> /dev/null; then
    echo "❌ Docker 未安装，请先安装 Docker"
    exit 1
fi

# 检查 Docker Compose 是否安装
if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose 未安装，请先安装 Docker Compose"
    exit 1
fi

# 检查 .env 文件是否存在
if [ ! -f .env ]; then
    echo "⚠️  未找到 .env 文件"
    echo "📝 正在从 .env.example 创建 .env 文件..."
    cp .env.example .env
    echo "✅ .env 文件已创建"
    echo ""
    echo "⚠️  请编辑 .env 文件，配置必要的环境变量，特别是："
    echo "   - OPENAI_API_KEY (API 密钥)"
    echo "   - BOCHA_API_KEY (API 密钥)"
    echo ""
    read -p "按回车键继续..."
fi

# 创建必要的目录
echo "📁 创建必要的目录..."
mkdir -p backend/logs
mkdir -p uploads
echo ""

# 停止现有容器
echo "🛑 停止现有容器..."
docker-compose down
echo ""

# 构建镜像
echo "🔨 构建 Docker 镜像..."
echo "   这可能需要几分钟时间，请耐心等待..."
docker-compose build
echo ""

# 启动服务
echo "🚀 启动所有服务..."
docker-compose up -d
echo ""

# 等待服务启动
echo "⏳ 等待服务启动..."
sleep 10

# 检查服务状态
echo "📊 服务状态:"
docker-compose ps
echo ""

# 显示访问信息
echo "========================================"
echo "  服务访问信息"
echo "========================================"
echo ""
echo "🌐 前端服务：http://localhost"
echo "🔧 后端 API:   http://localhost:8080"
echo "📊 Milvus 管理界面：http://localhost:3000"
echo "🐰 RabbitMQ 管理界面：http://localhost:15672"
echo "🗄️ Neo4j 管理界面：http://localhost:7474"
echo "📦 MinIO 管理界面：http://localhost:9001"
echo ""
echo "========================================"
echo ""

# 查看日志
echo "💡 提示:"
echo "   - 查看日志：docker-compose logs -f [服务名]"
echo "   - 停止服务：docker-compose down"
echo "   - 重启服务：docker-compose restart"
echo ""
