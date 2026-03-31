#!/bin/bash

set -e

echo "========================================="
echo "  NotifyHub K8s 部署脚本"
echo "========================================="

# 检查 kubectl 是否安装
if ! command -v kubectl &> /dev/null; then
    echo "错误：kubectl 未安装"
    exit 1
fi

# 检查当前上下文
CONTEXT=$(kubectl config current-context 2>/dev/null || echo "")
if [ -z "$CONTEXT" ]; then
    echo "错误：未配置 kubectl 上下文"
    exit 1
fi

echo "当前 K8s 上下文：$CONTEXT"
echo ""

# 确认部署
read -p "确认要部署到 $CONTEXT 吗？(y/n): " confirm
if [ "$confirm" != "y" ]; then
    echo "取消部署"
    exit 0
fi

# 创建命名空间和 Secret
echo "创建命名空间和 Secret..."
kubectl apply -f deploy/common/

# 构建 Docker 镜像（如果需要）
read -p "需要构建 Docker 镜像吗？(y/n): " build_image
if [ "$build_image" = "y" ]; then
    echo "构建 Gateway 镜像..."
    docker build -f docker/Dockerfile.gateway -t notify-hub/gateway:latest .
    
    echo "构建 Worker 镜像..."
    docker build -f docker/Dockerfile.worker -t notify-hub/worker:latest .
    
    # 推送镜像（如果需要）
    read -p "需要推送镜像到仓库吗？(y/n): " push_image
    if [ "$push_image" = "y" ]; then
        docker push notify-hub/gateway:latest
        docker push notify-hub/worker:latest
    fi
fi

# 部署 Gateway
echo "部署 Gateway..."
kubectl apply -f deploy/gateway/

# 部署 Worker
echo "部署 Worker..."
kubectl apply -f deploy/worker/

# 等待部署完成
echo "等待部署完成..."
kubectl rollout status deployment/notify-gateway -n notify-hub
kubectl rollout status deployment/notify-worker -n notify-hub

echo ""
echo "========================================="
echo "  部署完成"
echo "========================================="
echo ""
echo "查看 Pod 状态：kubectl get pods -n notify-hub"
echo "查看服务状态：kubectl get svc -n notify-hub"
echo "查看日志：kubectl logs -f deployment/notify-gateway -n notify-hub"
echo ""
