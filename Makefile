# Makefile — 项目根目录
.PHONY: help dev stop build test clean

help:  ## 显示帮助
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

dev:  ## 启动本地开发环境（中间件）
	docker compose -f docker/docker-compose.yml up -d
	@echo "✅ MySQL/Redis/Kafka/Nacos/MinIO 已启动"

stop:  ## 停止本地环境
	docker compose -f docker/docker-compose.yml down

build:  ## 编译所有后端服务
	cd campus-platform-backend && mvn clean package -DskipTests

test:  ## 运行单元测试
	cd campus-platform-backend && mvn test

clean:  ## 清理构建产物
	cd campus-platform-backend && mvn clean
	@echo "✅ 构建产物已清理"
