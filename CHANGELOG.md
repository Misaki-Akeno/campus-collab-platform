# Changelog

## [Unreleased] — 2026-04-13 脚手架全面夯实重构

### 修复

- **`campus-common` `Result<T>`**：字段 `message` → `msg`，`timestamp` → `traceId`，与白皮书 API 规范对齐；统一静态方法为 `ok()` / `fail()`
- **`campus-common` `ErrorCode`**：秒杀域错误码从 1031-1035 修正为白皮书规定的 5001-5005（`STOCK_EMPTY` / `DUPLICATE_BOOK` / `ACTIVITY_NOT_START` / `ACTIVITY_ENDED` / `FILE_UPLOAD_FAIL`）；新增 `TOO_MANY_REQUESTS(429)` / `USER_DISABLED` / `RECALL_TIMEOUT` 等缺失枚举
- **`campus-common` `GlobalExceptionHandler`**：适配新 `Result.fail()` 方法；新增 `MethodArgumentNotValidException` 处理
- **`campus-gateway` `JwtAuthFilter`**：JWT 解析从静态工具方法改为注入 `JwtUtil` Bean；转发请求时移除原始 `Authorization` Header 防止内部滥用
- **`campus-gateway` `application.yml`**：Gateway 路由补充 `StripPrefix=1` 过滤器（路由前缀 `/user/**` → 内部 `/api/v1/**`）；补充 JWT 配置节点
- **`docker/docker-compose.yml`**：Redis 镜像版本 `redis:8.6.2`（不存在）→ `redis:7.4.8`；Kafka 版本 `4.0.2` → `4.0.1`（与白皮书对齐）；所有服务增加 `restart: unless-stopped` 和 `healthcheck`

### 新增

- **`campus-common` `JwtProperties`**：`@ConfigurationProperties(prefix = "campus.jwt")` 配置类，统一管理 JWT 密钥和过期时间，Gateway 与业务服务共享
- **`campus-common` `JwtUtil`**：重构为 Spring `@Component`，通过构造注入 `JwtProperties`；增加 `getUserId(Claims)` / `getRole(Claims)` / `getUsername(Claims)` 便捷方法
- **`campus-user-service` `UserService` / `UserServiceImpl`**：完整实现注册/登录/刷新Token/获取我的信息四个核心接口；BCrypt 密码加密；refreshToken 存入 Redis 单端登录控制
- **`campus-user-service` `AuthController`**：补全 `GET /api/v1/me`、`POST /api/v1/token/refresh` 端点；从 Gateway 注入的 `X-User-Id` Header 读取用户身份
- **`campus-user-service` DTO 层**：`RegisterRequest` 新增密码强度、用户名格式校验；新建 `TokenRefreshRequest` / `TokenRefreshResponse` / `MeResponse`；`LoginResponse` 结构改为含嵌套 `UserInfo`

### 变更

- **`campus-user-service` `pom.xml`**：移除 `spring-boot-starter-security`（鉴权由 Gateway 统一处理）；新增 `spring-security-crypto`（仅 BCrypt）、`spring-boot-starter-validation`、`spring-boot-starter-data-redis`
- **各微服务 `application.yml`**：MyBatis-Plus `log-impl` 由 `StdOutImpl` 改为 `Slf4jImpl`；补充 `allowPublicKeyRetrieval=true`、Hikari 连接池配置；各服务 Redis database 编号隔离（0-4）
- **`ai-bot/requirements.txt`**：新增 `anthropic>=0.25.0` / `langchain-core>=0.2.0` / `redis>=5.0.0` / `aiomysql` / `aiokafka`

---

## [0.1.0] — 2026-04-12 初始脚手架搭建

### 新增

- 完整多模块 Maven 后端工程（`campus-platform-backend`）
- 5 个业务微服务空壳 + Gateway + campus-common + campus-api
- Docker Compose 本地开发环境（MySQL 8.4 / Redis / Kafka KRaft / Nacos / MinIO）
- 全量建库建表 SQL（`docker/mysql/init.sql` + `docs/sql/V1.0__init.sql`）
- React Native 前端目录骨架（`campus-platform-frontend`）
- FastAPI AI-Bot 空壳服务（`ai-bot`）
- GitHub Actions CI 配置（`.github/workflows/ci.yml`）
- Makefile 快捷命令（`make dev / stop / build / test / clean`）
