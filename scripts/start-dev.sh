#!/bin/bash

set -e

echo "========================================="
echo "  NotifyHub 本地开发环境启动脚本"
echo "========================================="

# 检查 Docker 是否安装
if ! command -v docker &> /dev/null; then
    echo "错误：Docker 未安装，请先安装 Docker"
    exit 1
fi

# 检查 Docker Compose 是否安装
if ! command -v docker-compose &> /dev/null; then
    echo "错误：Docker Compose 未安装"
    exit 1
fi

# 创建日志目录
mkdir -p logs/gateway logs/worker

# 停止旧容器
echo "停止旧容器..."
docker-compose down 2>/dev/null || true

# 启动服务
echo "启动服务（MySQL + Redis + Gateway + Worker + Prometheus + Grafana）..."
docker-compose up -d

# 等待服务启动
echo "等待服务启动..."
sleep 10

# 检查服务状态
echo ""
echo "========================================="
echo "  服务状态"
echo "========================================="
docker-compose ps

echo ""
echo "========================================="
echo "  访问地址"
echo "========================================="
echo "Gateway API:    http://localhost:8080/api"
echo "Worker API:     http://localhost:8081/api"
echo "Prometheus:     http://localhost:9090"
echo "Grafana:        http://localhost:3000 (admin/admin123)"
echo ""
echo "查看日志：docker-compose logs -f [gateway|worker]"
echo "停止服务：docker-compose down"
echo "========================================="
