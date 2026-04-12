# 20260412校园社团协作平台脚手架搭建进度

## 状态摘要

基础脚手架已完成，项目结构按白皮书第6节规范搭建，Maven 编译已通过 (`mvn clean compile`)。

## 已完成内容

### 1. 工程目录结构
- 创建了完整的多模块 Maven 后端工程（`campus-platform-backend`）
- 创建了 React Native 前端目录（`campus-platform-frontend`）
- 创建了 Python AI-Bot 目录（`ai-bot`）
- 创建了 Docker 本地开发环境编排（`docker/`）
- 创建了 CI 配置（`.github/workflows/ci.yml`）和 VSCode 工作区配置（`.vscode/`）

### 2. 后端模块 POM 与基础配置
- `campus-platform-backend/pom.xml` — 父 POM，统一管理版本（Spring Boot 3.2.12 / Spring Cloud 2023.0.3 / SCA 2023.0.1.2）
- `campus-common` — 公共模块 POM
- `campus-api` — Feign 契约模块 POM
- `campus-gateway` — 网关模块 POM
- `campus-user-service` / `campus-club-service` / `campus-im-service` / `campus-seckill-service` / `campus-file-service` — 5 个业务微服务 POM
- 所有服务均配置了 `application.yml` 和 `bootstrap.yml`（Nacos 注册/配置中心）

### 3. campus-common 公共代码
- `Result<T>` — 统一响应包装
- `ErrorCode` — 错误码枚举（按白皮书码段划分）
- `BizException` / `GlobalExceptionHandler` — 全局异常体系
- `BaseEntity` / `PageRequest` — 公共模型
- `JwtUtil` — JWT 生成/解析工具
- `SnowflakeIdUtil` — 雪花算法 ID 生成
- `RedisKeyConstant` / `KafkaTopicConstant` — 公共常量
- `MyBatisPlusMetaObjectHandler` — 自动填充 `create_time` / `update_time` / `is_deleted`

### 4. campus-gateway 网关
- `GatewayApplication.java` — 启动类
- `JwtAuthFilter` — 全局 JWT 鉴权过滤器（含白名单、X-User-Id/X-User-Role Header 注入）
- `application.yml` — 路由配置（user/club/im/seckill/file）

### 5. 业务微服务空壳
- 5 个业务服务均已创建启动类、包扫描、MyBatis-Plus MapperScan
- `campus-user-service` 额外提供了示例代码（`SysUser` 实体、`UserMapper`、登录/注册 DTO、`AuthController` 壳子）
- `campus-api` 提供了 `UserFeignClient` + `UserBasicDTO` + FallbackFactory 示例

### 6.本地开发基础设施
- `docker/docker-compose.yml` — 一键启动 MySQL 8.4 / Redis 8.6.2 / Kafka 4.0.2 / Nacos 2.5.2 / MinIO（MinIO API 端口已调整为 9002，避免与 gateway 9000 冲突）
- `docker/mysql/init.sql` — 全量建库建表脚本（覆盖 user/club/seckill/im/file 五域）
- `docs/sql/V1.0__init.sql` — 数据库脚本版本化备份
- `Makefile` — 提供 `make dev / stop / build / test / clean` 快捷命令

### 7. 前端与 AI 侧
- `campus-platform-frontend/package.json` — React Native 0.73 依赖骨架
- `ai-bot/requirements.txt` + `app/main.py` — FastAPI 空壳服务

## 尚未开始/待后续填充

- **业务逻辑实现**：各微服务的 Service / Controller / Mapper XML 方法尚未完整实现
- **Gateway Sentinel 限流**：仅预留配置位，未配置具体规则
- **WebSocket IM 核心**：`im-service` 的 WebSocket Server / SessionManager / Dispatcher 仅目录存在
- **秒杀 Lua 脚本**：`seckill-service` 的 Redis Lua 扣库存逻辑待实现
- **文件上传 OSS 封装**：`file-service` 的 MinIO SDK 配置和分片逻辑待实现
- **前端页面**：React Native screens 均为空目录
- **AI Agent RAG / Tools**：`ai-bot` 仅提供 health 接口

