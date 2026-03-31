#!/bin/bash

set -e

echo "========================================="
echo "  NotifyHub 构建脚本"
echo "========================================="

# 检查 Maven 是否安装
if ! command -v mvn &> /dev/null; then
    echo "错误：Maven 未安装，请先安装 Maven"
    exit 1
fi

echo "开始构建..."

# 清理并构建
mvn clean package -DskipTests

echo ""
echo "========================================="
echo "  构建完成"
echo "========================================="
echo "Gateway: notify-gateway/target/notify-gateway-*.jar"
echo "Worker:  notify-worker/target/notify-worker-*.jar"
echo ""

# 显示文件大小
echo "构建产物大小:"
ls -lh notify-gateway/target/*.jar 2>/dev/null || true
ls -lh notify-worker/target/*.jar 2>/dev/null || true
