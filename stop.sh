#!/bin/bash

# AI Agent 项目 Docker 停止脚本

echo "========================================"
echo "  AI Agent - Docker 停止脚本"
echo "========================================"
echo ""

# 停止所有服务
echo "🛑 停止所有 Docker 服务..."
docker-compose down
echo ""

# 询问是否删除数据卷
read -p "是否删除数据卷（这将删除所有数据）？(y/N): " confirm
if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
    echo "🗑️  删除数据卷..."
    docker-compose down -v
    echo "✅ 数据卷已删除"
else
    echo "✅ 数据卷已保留"
fi

echo ""
echo "========================================"
echo "  服务已停止"
echo "========================================"
