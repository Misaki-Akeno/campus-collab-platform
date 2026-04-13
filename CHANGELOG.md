# Changelog

## [Unreleased] — 2026-04-13 代码审查 Round 2 修复

### 修复（Critical）

- **`campus-seckill-service` `SeckillServiceImpl.book()`**：补充活动状态校验（`status != 1` 时拦截已取消活动）

### 修复（Breaking Change 撤销）

- **`campus-seckill-service` `book()` 返回值**：从临时 token（`activityId:userId`）改回真实 `orderId`（雪花预生成），客户端可直接轮询 `/api/v1/orders/{orderId}`，避免前端对接断链
- **`campus-seckill-service` `SeckillServiceImpl.book()`**：预创建 PROCESSING 状态订单 → Lua 扣减 → Kafka 异步更新成功/失败回滚，订单全生命周期可追溯

### 修复（Important）

- **`campus-file-service` `UploadServiceImpl`**：`uploadStatus` 魔法数字消除，新增 `UploadStatus` 常量类（UPLOADING=0 / COMPLETED=1），与 `ClubStatus` 保持统一风格
- **`campus-seckill-service` `SeckillOrder`**：status 注释从 "0-排队中 1-成功 2-已取消" 修正为 "0-排队中 1-成功 2-失败/已取消"，语义对齐
- **`campus-im-service` `ImServiceImpl.syncMessages()`**：全量 LIMIT 500 注释补充说明会话配额不均等已知限制（Phase 3 Kafka offset 解决）
- **`campus-club-service` `Club`**：status 注释补充 `3-审核拒绝`，与 DDL 对齐

### 新增

- `ACTIVITY_CANCELLED(5005)` 错误码
- `UploadStatus` 常量类

---

## [Unreleased] — 2026-04-13 代码审查意见修复

### 修复（Critical）

- **`campus-api` `CampusApiAutoConfiguration`**：`@Configuration` 改为 `@AutoConfiguration`，符合 Spring Boot 3 SPI 的 ordering/conditional 语义
- **`campus-club-service` `ClubServiceImpl.approveClub()`**：拒绝状态从 `2`（已解散）修正为 `3`（审核拒绝），新增 `ClubStatus` 常量类消除魔法数字；`docs/sql/V1.0__init.sql` club.status 注释同步补充 `3-审核拒绝`
- **`campus-gateway` `GatewayExceptionHandler`**：`handle()` 入口增加 `isCommitted()` 检查，防止响应已提交时写入 body 抛 `IllegalStateException`
- **`campus-gateway` `JwtAuthFilter`**：移除 `/seckill/api/v1/activities/**` 通配符白名单，改为精确列举 `/activities` 和 `/activities/{id}` — 修复 POST `/book` 可绕过鉴权的安全漏洞

### 修复（Important）

- **`campus-common` `SnowflakeIdUtil`**：移除 `@Component`（类仅含静态方法，注入无意义），添加私有构造函数强制静态调用
- **`campus-club-service` `ClubServiceImpl.joinClub()`**：`memberCount + 1` 改为 `setSql("member_count = member_count + 1")` 原子 SQL 递增，消除并发丢失更新
- **`campus-im-service` `ImServiceImpl.syncMessages()`**：全量拉取路径消除 N+1，改为单次 `IN (convId1, convId2, ...)` 批量查询
- **`campus-seckill-service` `SeckillServiceImpl.book()`**：重构为正确的高并发流程 — Redis Lua 原子扣减 → Kafka 异步落库，移除 Lua 前创建 DB 订单的错误逻辑；新增 `lua/seckill_deduct.lua` 脚本（防重 + 库存扣减 + SADD 原子三步）
- **`campus-file-service` `FileMeta`**：`createTime` / `updateTime` 补 `@TableField(fill = FieldFill.INSERT/INSERT_UPDATE)` 注解，MetaObjectHandler 现在会自动填充
- **`campus-im-service` `ImConversation` / `ImMessage`**：同上，补 `@TableField` 注解及 `@TableLogic` 逻辑删除
- **`campus-club-service` `ClubController.approveClub()`**：权限不足由 `return Result.fail()` 改为 `throw new BizException(FORBIDDEN)`，统一由 `GlobalExceptionHandler` 处理
- **`campus-seckill-service` `SeckillController.getOrder()`**：补充订单归属校验（userId 不匹配返回 403），防止越权查询他人订单

---

## [Unreleased] — 2026-04-13 脚手架问题修复 + 四服务骨架补全

### 修复

- **`campus-gateway` `JwtAuthFilter`**：白名单从精确字符串匹配改为 AntPath 模式，公开集合接口加 `/**` 通配，避免带查询参数时鉴权失效
- **`campus-club-service` `pom.xml`**：移除不应引入的 `spring-boot-starter-security`，替换为 `spring-security-crypto`（BCrypt only）+ `spring-boot-starter-validation`
- **各业务服务 `application.yml`**（user/club/seckill/file/im）：MyBatis-Plus `global-config.db-config` 补充 `id-type: assign_id`，与 `BaseEntity @TableId(type = IdType.ASSIGN_ID)` 显式对齐
- **`campus-common` `SnowflakeIdUtil`**：添加 `@Component` 注解，使其可作为 Spring Bean 注入（静态方法保留，供无 Spring 上下文场景使用）

### 新增

- **`campus-gateway` `GatewayExceptionHandler`**：WebFlux 栈全局异常处理器（`@Order(-1)`），统一返回 JSON 格式错误响应，覆盖 Spring 默认 Whitelabel 页面
- **`campus-api` `CampusApiAutoConfiguration`**：通过 Spring Boot SPI（`AutoConfiguration.imports`）自动注册 `@EnableFeignClients`，消费方引入依赖后无需手动配置扫描路径
- **`campus-club-service`**：补全 `Club` / `ClubMember` / `ClubAnnouncement` 实体，三个 Mapper，`ClubService`（Interface + Impl），`ClubController`（列表/详情/创建/审核/加入）
- **`campus-seckill-service`**：补全 `SeckillActivity` / `SeckillOrder` 实体，两个 Mapper，`SeckillService`（Interface + Impl，含 Redis 防重 + Kafka 异步下单骨架），`SeckillController`
- **`campus-file-service`**：补全 `FileMeta` 实体，`FileMetaMapper`，`UploadService`（Interface + Impl，含秒传/断点续传判断逻辑，MinIO 预签名 URL 为 TODO），`UploadController`
- **`campus-im-service`**：补全 `ImConversation` / `ImMessage` / `ImConversationMember` 实体，三个 Mapper，`ImService`（Interface + Impl，会话列表 + 离线消息同步），`ImController`

### 说明

- `UploadServiceImpl` MinIO 实际调用（OssService）留有 `TODO` 注释，Phase 2 补充
- IM WebSocket（`WsServer` 等）Phase 3 实现，当前仅 REST 骨架

---

## [Unreleased] — 2026-04-13 文档体系拆分重构

### 新增

- **`ARCHITECTURE.md`**（根目录）：从白皮书提炼的架构总览文档，含微服务拓扑图、技术选型、ER 图、安全体系、部署架构、SLA、里程碑、工程规范速查
- **`docs/API.md`**：独立接口契约文档，包含全部 15+ REST API（完整 Request/Response）+ WebSocket 指令集 + 全局异常码表
- **各子项目 `CLAUDE.md`**：为 campus-gateway / campus-user-service / campus-club-service / campus-im-service / campus-seckill-service / campus-file-service / campus-common / campus-api / campus-platform-frontend / ai-bot 共 10 个子项目创建各自的开发指南

### 变更

- **根目录 `CLAUDE.md`**：从 2 行占位说明扩展为完整 AI 上下文注入，包含核心文档引用表、快速启动命令、子项目入口表、编码规范速查、Git 分支策略

---

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
