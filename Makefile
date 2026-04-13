# Makefile — 项目根目录
.PHONY: help dev stop build test clean run-gateway run-user run-club run-seckill run-im run-file

# 加载 .env（文件存在时）；-include 在文件缺失时不报错
-include .env
export

help:  ## 显示帮助
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

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

run-gateway:  ## 本地启动 API 网关（需先 make build）
	@[ -f .env ] || (echo "❌ 缺少 .env 文件，请复制 .env.example 并填入密钥" && exit 1)
	java -jar campus-platform-backend/campus-gateway/target/campus-gateway-*.jar

run-user:  ## 本地启动用户服务（需先 make build）
	@[ -f .env ] || (echo "❌ 缺少 .env 文件，请复制 .env.example 并填入密钥" && exit 1)
	java -jar campus-platform-backend/campus-user-service/target/campus-user-service-*.jar

run-club:  ## 本地启动社团服务（需先 make build）
	@[ -f .env ] || (echo "❌ 缺少 .env 文件，请复制 .env.example 并填入密钥" && exit 1)
	java -jar campus-platform-backend/campus-club-service/target/campus-club-service-*.jar

run-seckill:  ## 本地启动秒杀服务（需先 make build）
	@[ -f .env ] || (echo "❌ 缺少 .env 文件，请复制 .env.example 并填入密钥" && exit 1)
	java -jar campus-platform-backend/campus-seckill-service/target/campus-seckill-service-*.jar

run-im:  ## 本地启动 IM 服务（需先 make build）
	@[ -f .env ] || (echo "❌ 缺少 .env 文件，请复制 .env.example 并填入密钥" && exit 1)
	java -jar campus-platform-backend/campus-im-service/target/campus-im-service-*.jar

run-file:  ## 本地启动文件服务（需先 make build）
	@[ -f .env ] || (echo "❌ 缺少 .env 文件，请复制 .env.example 并填入密钥" && exit 1)
	java -jar campus-platform-backend/campus-file-service/target/campus-file-service-*.jar
