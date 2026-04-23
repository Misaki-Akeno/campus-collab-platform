# Makefile — 项目根目录
.PHONY: help dev stop build test test-all clean \
        run-gateway run-user run-club run-seckill run-im run-file run-all stop-all \
        http-test http-test-ci wait-healthy wait-services

# 加载 .env（文件存在时）；-include 在文件缺失时不报错
-include .env
export

# 配置
GATEWAY_URL := http://localhost:9000
WAIT_TIMEOUT := 240
SERVICES := campus-gateway campus-user-service campus-club-service \
            campus-seckill-service campus-im-service campus-file-service

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

# ============================================================
# 全自动测试流水线
# ============================================================

test-all:  ## 全自动测试：单测 + HTTP 集成测试
	@trap '$(MAKE) stop-all' INT TERM EXIT; \
	set -e; \
	echo "▶ 阶段 1/5: 启动中间件..."; \
	$(MAKE) dev; \
	$(MAKE) wait-healthy; \
	echo "▶ 阶段 2/5: 编译服务..."; \
	$(MAKE) build; \
	echo "▶ 阶段 3/5: 启动所有服务..."; \
	$(MAKE) run-all; \
	$(MAKE) wait-services; \
	echo "▶ 阶段 4/5: 运行单元测试..."; \
	$(MAKE) test; \
	echo "▶ 阶段 5/5: 运行 HTTP 集成测试..."; \
	$(MAKE) http-test-ci; \
	echo ""; \
	echo "✅ 全部测试完成，开始清理..."; \
	$(MAKE) stop-all; \
	echo "✅ 环境已清理"

# ============================================================
# 服务启停
# ============================================================

run-all:  ## 后台启动所有服务（需先 make build）
	@[ -f .env ] || (echo "❌ 缺少 .env 文件，请复制 .env.example 并填入密钥" && exit 1)
	@mkdir -p logs
	@export $$(grep -v '^#' .env | grep -v '^$$' | sed 's/[[:space:]]//g'); \
	for svc in $(SERVICES); do \
		echo "▶ 启动 $$svc..."; \
		nohup java -jar campus-platform-backend/$$svc/target/$$svc-*.jar > logs/$$svc.log 2>&1 & \
		echo $$! > logs/$$svc.pid; \
	done
	@echo "✅ 所有服务已在后台启动 (PID 记录在 logs/*.pid)"

stop-all:  ## 停止所有服务和中间件
	@-for svc in $(SERVICES); do \
		if [ -f logs/$$svc.pid ]; then \
			kill $$(cat logs/$$svc.pid) 2>/dev/null || true; \
			rm -f logs/$$svc.pid; \
			echo "🛑 已停止 $$svc"; \
		fi; \
	done
	$(MAKE) stop

# ============================================================
# HTTP 测试（Bruno CLI）
# ============================================================

http-test:  ## 交互模式运行 Bruno HTTP 测试
	@command -v bru >/dev/null 2>&1 || { echo "❌ Bruno CLI 未安装，运行: npm install -g @usebruno/cli"; exit 1; }
	cd tests/bruno && ts=$$(date +%s) && bru run --env-file environments/local.json \
		--env-var test_username=testuser_bruno_$$ts \
		--env-var test_email=testuser_bruno_$$ts@campus.edu \
		--env-var access_token= \
		--env-var refresh_token= \
		--env-var registered_user_id= \
		--env-var registered_club_id= \
		--env-var registered_activity_id= \
		--env-var registered_order_id= \
		--env-var registered_upload_id= \
		--env-var registered_file_md5= \
		user-service club-service seckill-service im-service file-service

http-test-ci:  ## CI 模式运行 Bruno HTTP 测试（JSON 输出 + 遇错即停）
	@command -v bru >/dev/null 2>&1 || { echo "❌ Bruno CLI 未安装，运行: npm install -g @usebruno/cli"; exit 1; }
	@mkdir -p test-results
	cd tests/bruno && ts=$$(date +%s) && bru run --env-file environments/local.json \
		--env-var test_username=testuser_bruno_$$ts \
		--env-var test_email=testuser_bruno_$$ts@campus.edu \
		--env-var access_token= \
		--env-var refresh_token= \
		--env-var registered_user_id= \
		--env-var registered_club_id= \
		--env-var registered_activity_id= \
		--env-var registered_order_id= \
		--env-var registered_upload_id= \
		--env-var registered_file_md5= \
		--reporter json --output ../../test-results/bruno.json \
		user-service club-service seckill-service im-service file-service

# ============================================================
# 健康检查等待
# ============================================================

wait-healthy:  ## 等待中间件全部健康
	@echo "⏳ 等待中间件就绪（超时 ${WAIT_TIMEOUT}s）..."
	@timeout=${WAIT_TIMEOUT}; \
	deadline=$$(($$(date +%s) + timeout)); \
	while true; do \
		[ $$(date +%s) -gt $$deadline ] && { echo "❌ 中间件启动超时"; exit 1; }; \
		all_healthy=true; \
		(bash -c 'echo >/dev/tcp/127.0.0.1/3306' 2>/dev/null) || all_healthy=false; \
		(bash -c 'echo >/dev/tcp/127.0.0.1/6379' 2>/dev/null) || all_healthy=false; \
		(bash -c 'echo >/dev/tcp/127.0.0.1/9092' 2>/dev/null) || all_healthy=false; \
		curl -sf http://localhost:8848/nacos/v1/console/health/liveness >/dev/null 2>&1 || all_healthy=false; \
		curl -sf http://localhost:9002/minio/health/live >/dev/null 2>&1 || all_healthy=false; \
		if $$all_healthy; then \
			echo "✅ 中间件全部就绪"; \
			break; \
		fi; \
		sleep 2; \
	done

wait-services:  ## 等待所有 Java 服务就绪
	@echo "⏳ 等待服务启动..."
	@timeout=${WAIT_TIMEOUT}; \
	start=$$(date +%s); \
	deadline=$$((start + timeout)); \
	ready=0; total=$(words $(SERVICES)); \
	while [ $$ready -lt $$total ]; do \
		[ $$(date +%s) -gt $$deadline ] && { echo "❌ 服务启动超时 (就绪 $$ready/$$total)"; echo "📋 检查日志: ls logs/"; exit 1; }; \
		ready=0; \
		for port in 9000 8081 8082 8083 8084 8085; do \
			curl -sf http://localhost:$$port/actuator/health >/dev/null 2>&1 && ready=$$((ready + 1)); \
		done; \
		echo "  [$$ready/$$total] 服务就绪..."; \
		sleep 3; \
	done
	@echo "✅ 所有服务已就绪"

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
