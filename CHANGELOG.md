# Changelog

## [Unreleased] — 2026-04-23 CI 修复：Wait for services 超时（第二次）

### Bug Fix

- **所有服务 `application.yml`**：Hikari `minimum-idle` 5→1，`maximum-pool-size` 20→10，减少启动时并发连接建立数量，加快 Spring Boot 冷启动
- **`Makefile` / workflow**：`WAIT_TIMEOUT` 180→240s 保底

## [Unreleased] — 2026-04-23 CI 修复：Wait for services 超时

### Bug Fix

- **`docker/docker-compose.yml`**：Kafka healthcheck `start_period` 从 30s 缩短为 15s，`interval` 从 15s 缩短为 10s，`retries` 从 10 降为 8，确保在 120s 内完成健康检查
- **`Makefile`**：`WAIT_TIMEOUT` 从 120s 提高至 180s；`wait-healthy` 中 Kafka 端口检测从 `|| true`（忽略）改为 `|| all_healthy=false`（纳入健康门控）；`stop-all` 移除 `rm -rf logs` 以保留失败日志
- **`.github/workflows/backend-quality-gate.yml`**：`Wait for middleware` / `Wait for services` 改用 `make wait-healthy` / `make wait-services` 复用 Makefile 逻辑，超时统一设为 180s；`Upload service logs` 步骤移至 `Cleanup` 之前，避免 `stop-all` 清理后日志丢失

## [Unreleased] — 2026-04-19 CI/CD 流水线重构：质量门拆分 + Tag 自动 Release

### CI/CD 变更

- **`.github/workflows/backend-quality-gate.yml`**：新增后端独立质量门，覆盖构建、SpotBugs 静态分析、单元测试、Bruno HTTP 集成测试
- **`.github/workflows/backend-quality-gate.yml`**：中间件等待阶段统一改为读取容器健康状态（MySQL/Redis/Nacos/MinIO），不依赖 Runner 预装 `redis-cli` 或 `mysqladmin`
- **`.github/workflows/frontend-quality-gate.yml`**：新增前端独立质量门，单独执行依赖安装与 ESLint 校验
- **`.github/workflows/release-on-tag.yml`**：新增 Tag 自动触发 Release（`v*`），自动构建后端 JAR，打包 release bundle 并发布 GitHub Release 资产，为后续接入 CD 提前准备制品输出规范

## [Unreleased] — 2026-04-17 Phase 2 P0 完成：MinIO 集成 + 安全修复

### 新增功能

- **`campus-file-service` MinIO 分片上传全链路**：
  - `OssService` 接口 + `OssServiceImpl` 实现：`initMultipartUpload` / `generatePresignedPutUrl` / `completeMultipartUpload` / `abortMultipartUpload`
  - `OssConfig`（`CampusMinioClient` Bean）从 `application.yml` 读取 MinIO 端点/密钥
  - `MinioProperties` 配置类（`@ConfigurationProperties(prefix = "minio")`）
  - `initUpload` 全新上传路径返回真实预签名 PUT URL 列表，每个 URL 携带 `uploadId` 和 `partNumber` 查询参数
  - `merge` 调用 MinIO `completeMultipartUpload` 合并分片，返回真实 `fileUrl`
  - Redis 新增存储 `file:upload:object:{uploadId}` 和 `file:upload:minio:{uploadId}` 用于 merge 时还原对象键和 MinIO uploadId

- **`campus-im-service` 单元测试**（新增 4 用例，总计 71 用例）：
  - `ImServiceImplTest`：会话列表空集、合法会话消息同步、非法会话越权拦截、全量同步权限泄露防护

### 安全修复（Critical）

- **`campus-file-service` `UploadServiceImpl.merge()`**：增加 `uploadId ↔ fileMd5` 交叉校验，防止攻击者使用自己的 `uploadId` 合并他人的文件记录
- **`campus-file-service` `UploadServiceImpl.merge()`**：合并前校验 Redis `file:chunk:{uploadId}` 分片数量是否等于预期的 `chunkCount`，防止分片缺失时被恶意标记为完成
- **`campus-file-service` `UploadServiceImpl.merge()`**：增加状态机守卫，仅允许 `uploadStatus == UPLOADING` 的记录执行合并

### 技术说明

- MinIO Java SDK 8.x 的 `MinioClient` 不继承 `S3Base`，而是内部包装 `MinioAsyncClient`（`MinioAsyncClient extends S3Base`）。低层分片操作 API（`createMultipartUpload` / `completeMultipartUpload` / `abortMultipartUpload`）为 `S3Base` 的 `protected` 方法，通过 `MinioClient.asyncClient` 字段反射获取 `MinioAsyncClient` 实例后调用
- `generatePresignedPutUrl` 基于 `MinioClient.getPresignedObjectUrl()` 生成基础预签名 URL，手动拼接 `uploadId` 和 `partNumber` 查询参数（Multipart Upload 的 S3 协议要求）
- `MinioProperties` 启用 `@Validated` + `@NotBlank` 参数校验，配置缺失时启动即报错而非运行时 NPE
- Lombok annotation processing 在新模块中不稳定，OssServiceImpl/UploadServiceImpl 改用显式构造函数和 SLF4J Logger

---

## [Unreleased] — 2026-04-14 测试方案调整：移除集成测试，引入 Bruno HTTP 测试

### 测试基础设施变更
- **移除 Testcontainers 集成测试方案**：因环境依赖复杂度高、调试困难，移除所有基于 Testcontainers 的集成测试配置
- **引入 Bruno HTTP 测试集**（`tests/bruno/`）：轻量级 API 测试方案，用 `.bru` 文件定义测试用例，支持断言/环境变量/链路传递
- **新增 `make http-test` / `make http-test-ci` 目标**：通过 Bruno CLI (`bru run`) 自动化执行 API 测试，无需 Docker / Spring 上下文
- **保留完整单元测试**（67 用例），新增 HTTP 集成测试作为服务运行后的端到端验证手段

---

## [Unreleased] — 2026-04-13 设计问题全量修复

### 安全修复

- **所有服务 `application.yml`**：MySQL 密码、MinIO 密钥从硬编码改为 `${ENV_VAR}` 环境变量占位符（A-04）；`.env.example` 补全所有敏感变量模板
- **`campus-file-service` `UploadServiceImpl.initUpload()`**：秒传判断从仅校验 MD5 改为 `MD5 + fileSize` 双重校验，防止 Hash Flooding 攻击（A-06）

### 架构修复

- **`campus-seckill-service` `SeckillServiceImpl.book()`**：执行顺序从"先建单再 Lua"改为**先 Lua 后建单**，彻底消除"幽灵订单"（Lua 失败后无法回滚的悬空记录）；订单直接以 `SUCCESS(1)` 状态写入，Kafka 改为仅做异步对账（A-02）
- **`campus-api` `UserFeignClient`**：路径从 `/user/api/v1/users/{userId}/basic`（含 Gateway 前缀）修正为 `/api/v1/users/{userId}/basic`（直连服务内部端口，Feign 不经过 Gateway）（A-03）

### 新增功能

- **`campus-club-service` 公告管理**：实现 `createAnnouncement` / `listAnnouncements`，新增接口（D-04）：
  - `POST /api/v1/clubs/{clubId}/announcements`（社长/副社长发布，`memberRole >= 1`）
  - `GET /api/v1/clubs/{clubId}/announcements`（公开，分页，置顶优先）
- **`campus-club-service` 入社审批流程**：`joinClub` 从"直接加入"改为"申请待审批"（A-07）：
  - `club_member` 新增 `status` 字段（0-待审核 1-已通过 2-已拒绝），`join_time` 改为审批通过时才填写
  - 新增接口 `POST /api/v1/clubs/{clubId}/members/{memberId}/approve`（社长/副社长审批）
  - 新增错误码 `JOIN_REQUEST_EXISTS(1016)` / `JOIN_REQUEST_NOT_FOUND(1017)`
- **`campus-club-service` Feign 内部端点**：新增 `GET /api/v1/clubs/{clubId}/basic` 供 ClubFeignClient 调用
- **`campus-api`**：新增 `ClubFeignClient` + `ClubBasicDTO` + `ClubFeignClientFallbackFactory`（A-03）
- **`campus-api`**：新增 `FileFeignClient` + `FileBasicDTO` + `FileFeignClientFallbackFactory`（A-03）

### 接口变更（Breaking Change）

- **`campus-club-service` `POST /api/v1/clubs`**：创建社团参数从 `@RequestParam`（URL 查询参数）改为 `@RequestBody JSON`，支持参数校验注解（A-01）
  - 新增 `CreateClubRequest` DTO：`name`（必填，≤128字符）+ `description`（可选）+ `category`（可选）
- **`campus-seckill-service` `GET /api/v1/activities`**：新增可选过滤参数 `clubId` / `status`，与 API.md 文档对齐（D-08）

### 数据库变更

- `club_member` 表：新增 `status tinyint NOT NULL DEFAULT '0'`，新增索引 `idx_club_status(club_id, status)`；`join_time` 改为 `DEFAULT NULL`
- `seckill_order` 表：`status` 注释更新（去掉"排队中"，直接写入 SUCCESS）



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
