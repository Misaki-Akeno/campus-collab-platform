# 后端项目全量评估报告

> 评估日期：2026-04-13  
> 评估范围：`campus-platform-backend/` 全部 8 个模块  
> 参考文档：`ARCHITECTURE.md`、`docs/API.md`、`docs/sql/V1.0__init.sql`

---

## 一、已完善的部分

### 1.1 公共基础设施（campus-common）✅

| 组件 | 状态 | 说明 |
|------|------|------|
| `Result<T>` 统一响应包装 | ✅ 完整 | code/msg/data/traceId 四字段，ok/fail 工厂方法 |
| `ErrorCode` 枚举 | ✅ 完整 | 按服务分段（200/400-500/1001-1050/5001-5010），覆盖所有业务场景 |
| `BizException` + 全局异常处理 | ✅ 完整 | `@RestControllerAdvice` 捕获所有业务异常和参数校验异常 |
| `JwtUtil` + `JwtProperties` | ✅ 完整 | HMAC-SHA256，accessToken(2h) + refreshToken(7d)，携带 userId/username/role/jti |
| `SnowflakeIdUtil` | ✅ 完整 | 雪花 ID 生成静态方法 |
| `BaseEntity` | ✅ 完整 | id/createTime/updateTime/isDeleted，`@TableField` 注解齐全 |
| `MyBatisPlusMetaObjectHandler` | ✅ 完整 | 自动填充时间戳与逻辑删除 |
| `RedisKeyConstant` + `KafkaTopicConstant` | ✅ 完整 | 8 个 Redis Key 模板，2 个 Kafka Topic |

### 1.2 服务间通信基础（campus-api）✅（基础部分）

- `UserFeignClient`：定义了 `getUserBasic(Long userId)` 接口
- `CampusApiAutoConfiguration`：通过 Spring Boot SPI 自动装配，各服务引入即可用

### 1.3 用户服务（campus-user-service）✅ 完整

所有接口与 API.md 契约完全一致：

| 接口 | 实现状态 |
|------|---------|
| `POST /register` — 注册 | ✅ 用户名唯一性、BCrypt(12) 加密、默认 role=0 |
| `POST /login` — 登录 | ✅ 密码校验、双 Token 生成、Redis 存储 refreshToken、更新 lastLogin |
| `POST /token/refresh` — 刷新 Token | ✅ Token 解析、Redis 黑名单校验、重新生成 accessToken |
| `GET /me` — 获取当前用户信息 | ✅ 返回用户完整信息 |

### 1.4 社团服务主流程（campus-club-service）✅ 主要功能完整

| 接口 | 实现状态 |
|------|---------|
| `POST /clubs` — 创建社团 | ✅ 名称唯一性校验、创建者自动成为社长、状态 PENDING |
| `GET /clubs` — 社团列表（分页+分类） | ✅ 仅返回 ACTIVE 社团 |
| `GET /clubs/{clubId}` — 社团详情 | ✅ |
| `POST /clubs/{clubId}/approve` — 审核 | ✅ 管理员权限校验（role=2）、ACTIVE/REJECTED 状态更新 |
| `POST /clubs/{clubId}/join` — 加入社团 | ✅ 成员唯一性校验、`member_count = member_count + 1`（原子防并发） |

### 1.5 秒杀服务核心（campus-seckill-service）✅ 核心完整

| 特性 | 实现状态 |
|------|---------|
| `seckill_deduct.lua` — Redis 原子扣减 | ✅ 防重复（SISMEMBER）+ 扣减（DECR）+ 已报名记录（SADD），三步原子 |
| Lua 返回值语义 | ✅ ≥0 成功，-1 库存不足，-2 重复报名，-3 活动未预热 |
| `POST /activities/{id}/book` — 秒杀报名 | ✅ 预创建 PROCESSING 订单 → Lua 扣减 → Kafka 异步 → 返回 orderId |
| `GET /orders/{orderId}` — 查询订单 | ✅ 订单归属校验（防越权） |
| `GET /activities` — 活动列表 | ✅ 分页查询 |

### 1.6 网关（campus-gateway）✅ 基础功能完整

| 特性 | 状态 |
|------|------|
| JWT 鉴权 Filter | ✅ 解析 Token + 注入 X-User-Id / X-User-Role Header |
| 白名单（register/login/refresh/公开列表） | ✅ |
| CORS 配置 | ✅ |
| 禁止虚拟线程（保持 WebFlux Reactive） | ✅ 符合架构规范 |
| 网关异常处理 | ✅ |

### 1.7 数据库设计（docs/sql/V1.0__init.sql）✅ 完整

- **9 张表**全部完整：sys_user / club / club_member / club_announcement / seckill_activity / seckill_order / im_conversation / im_conversation_member / im_message / file_meta
- 所有表包含公共字段（id/create_time/update_time/is_deleted）
- 索引设计合理（UK、普通索引）
- utf8mb4 字符集

### 1.8 工程基础设施 ✅

| 要素 | 状态 |
|------|------|
| 多模块 Maven 聚合（pom.xml） | ✅ JDK 21 + Spring Boot 3.5.3 + Spring Cloud 2024.0.2 |
| 所有服务启用虚拟线程（Gateway 除外） | ✅ 符合架构决策 |
| Redis 按 db 隔离（db0~db4） | ✅ |
| Nacos 服务发现注册 | ✅ 各服务均已配置 |
| MyBatis-Plus 全局配置 | ✅ 逻辑删除、自动时间填充、分页插件 |
| BCrypt cost=12 密码强度 | ✅ |

---

## 二、基本完善但有缺口的部分

### 2.1 文件服务（campus-file-service）⚠️ 骨架完整，OSS 集成缺失

**已实现：**
- 秒传判断（MD5 查 file_meta）
- 断点续传识别（Redis Hash 查已上传分片）
- 分片 ETag 记录（`completeChunk`）
- 合并接口入口（`merge`）
- 文件元数据查询

**缺失（均有 TODO 注释标注为 Phase 2）：**

| 缺失功能 | 影响 |
|---------|------|
| `OssService` 类（MinIO SDK 封装）未实现 | `initUpload` 返回的 `presignedUrls` 始终为空数组，客户端无法实际上传 |
| MinIO Multipart Upload 初始化 | `uploadId` 字段返回硬编码占位符 `TODO_MINIO_UPLOAD_ID` |
| MinIO `Complete Multipart Upload` 调用 | `merge` 接口只更新数据库状态，未触发 OSS 合并，`fileUrl` 字段无真实值 |
| 预签名 URL 生成逻辑 | 客户端无法直传 OSS |

**文档 vs 代码不一致：**
- `API.md` 中 `presignedUrls` 应为分片直传 URL 列表，但代码始终返回 `[]`（`new ArrayList<>()`）
- `API.md` 中 `fileUrl` 合并后应返回真实 OSS 地址，当前代码返回空字符串

### 2.2 IM 服务（campus-im-service）⚠️ REST 骨架就绪，实时通信未实现

**已实现：**
- `GET /conversations` — 查询用户参与的所有会话
- `GET /messages/sync` — 离线消息同步（全量 LIMIT 500，支持指定会话和 lastMsgId）

**缺失（均标注为 Phase 3）：**

| 缺失功能 | 影响 |
|---------|------|
| WebSocket 端点（`@ServerEndpoint` 或 Spring WebSocket） | 实时消息收发完全不可用 |
| `WsSessionManager`（连接注册/注销/查找） | 无法管理在线用户连接 |
| Redis Pub/Sub 跨节点消息分发 | 多实例部署时消息无法路由 |
| Kafka Message Consumer | 消息无法持久化到 im_message 表 |
| ACK / 重试机制 | 消息可靠投递无保障 |
| 心跳保活 | 连接无法维持 |
| 撤回消息 / 已读回执逻辑 | 功能缺失 |

**代码内注释确认的已知限制：**
- `syncMessages` 全量拉取未做会话配额均衡（LIMIT 500 跨所有会话）
- Phase 3 计划换 Kafka offset 方案

### 2.3 秒杀服务 Kafka 消费端（campus-seckill-service）⚠️ Producer 完整，Consumer 缺失

**已实现：**
- Kafka Producer 配置（acks=all, retries=3）
- 秒杀成功后向 `campus-seckill-order` Topic 发送消息
- 客户端轮询 orderId 接口

**缺失：**
- `@KafkaListener` Consumer 类 — 订单状态未从 PROCESSING 更新为 SUCCESS/FAILED
- 死信队列（DLQ）处理
- 库存与订单数对账逻辑（ARCHITECTURE.md §7.2 提到需要告警）

**影响：** 当前所有秒杀订单永远停留在 `PROCESSING` 状态，`GET /orders/{orderId}` 查询结果永远不会变为 SUCCESS

### 2.4 社团服务公告管理（campus-club-service）⚠️ Entity 存在但业务逻辑缺失

**已实现：**
- `ClubAnnouncement` Entity 完整（clubId/title/content/publisherId/isPinned）
- `ClubAnnouncementMapper` 继承 BaseMapper

**缺失：**
- `ClubService` Interface 未定义 `createAnnouncement` / `listAnnouncements` 方法
- `ClubController` 无公告相关路由
- `API.md §B4` 定义了 `POST /clubs/{clubId}/announcements` 接口，但代码完全未实现

**文档 vs 代码不一致：** API.md 有完整的公告接口规范，代码中无对应实现。

### 2.5 Feign 跨服务调用（campus-api）⚠️ 定义不完整

**已实现：**
- `UserFeignClient.getUserBasic(Long userId)` 定义

**缺失：**
- `user-service` 中无对应的 `GET /internal/users/{userId}` REST 端点 — Feign Client 定义了但被调用方未实现
- `ClubFeignClient` — 未定义（API.md 注释"Phase 2 通过 ClubFeignClient 获取 clubName"）
- `FileFeignClient` — 未定义

**设计问题：** `UserFeignClient` 调用路径与 user-service 实际路由不对齐，运行时调用必然 404。

---

## 三、有待实现的部分

### 3.1 IM WebSocket 完整实现（Phase 3 核心）❌

根据 ARCHITECTURE.md 和 API.md §7 的规范，以下均未实现：

- [ ] WebSocket 握手端点 `ws://gateway:9000/im/ws?token=<JWT>`
- [ ] Gateway 侧 WebSocket JWT 校验和路由升级
- [ ] `WsServer` — `@OnOpen/@OnMessage/@OnClose` 处理
- [ ] `WsSessionManager` — 连接注册与在线状态（`im:online:{userId}`）
- [ ] 8 种 cmd 指令处理：CHAT_MSG / ACK / PUSH_MSG / HEARTBEAT / RECALL / READ_REPORT / TYPING / KICK_OFF
- [ ] Redis Pub/Sub — 跨 im-service 实例消息分发（`im:node:{nodeId}`）
- [ ] Kafka Consumer — `campus-im-message` Topic 消费 → 批量写入 im_message 表
- [ ] 消息幂等性校验（客户端 msgId 去重）
- [ ] ACK + 客户端重试机制
- [ ] 已读回执落库（更新 `im_conversation_member.read_msg_id`）
- [ ] 消息撤回（2 分钟限制，`is_recalled = 1`）

### 3.2 限流防刷规则（campus-gateway）❌

ARCHITECTURE.md §5.3 和 API.md §C1 定义的限流策略未落地：

- [ ] Sentinel 令牌桶（单接口 QPS ≤ 1000）
- [ ] 用户维度滑动窗口（同一用户 5s 内 ≤ 1 次）
- [ ] IP 维度滑动窗口（同一 IP 1s 内 ≤ 10 次）

当前网关 `application.yml` 仅有路由规则，无 Sentinel 配置。

### 3.3 内部接口 & Feign 调用链路❌

| 需补充接口 | 所属服务 | 调用方 | 场景 |
|----------|---------|--------|------|
| `GET /internal/users/{userId}` | user-service | campus-api UserFeignClient | club/seckill 展示用户昵称 |
| `ClubFeignClient` 定义 + 实现 | club-service | seckill-service | 活动列表中展示 clubName |
| `FileFeignClient` 定义 + 实现 | file-service | club/user 等 | 头像/logo URL 解析 |

### 3.4 秒杀活动管理接口（campus-seckill-service）❌

API.md §C3 提到 `?clubId=100001&status=1` 过滤参数，但代码中 `listActivities` 只做了分页，未做 clubId 和 status 过滤。

另外，**活动创建接口未实现**：无 `POST /activities` 接口，无法通过 REST 创建秒杀活动（只能直连数据库）。

### 3.5 用户管理扩展接口❌

- [ ] `GET /me` 返回的 `clubs` 字段（用户参与的社团列表）当前未实现，始终为 null（需跨服务查询）
- [ ] 用户头像上传/更新接口
- [ ] 密码修改接口（改密后应加入 refreshToken 黑名单）
- [ ] 登出接口（jti 加入黑名单）

### 3.6 社团成员管理扩展❌

- [ ] `GET /clubs/{clubId}/members` — 社团成员列表（API.md 未定义但架构需要）
- [ ] 社长审批入团申请（当前 `joinClub` 是直接加入，无审批流）
- [ ] 成员角色变更（社长/副社长/普通成员）
- [ ] 踢出成员接口

### 3.7 可观测性（全服务）❌

ARCHITECTURE.md §7 定义了三大支柱，均未落地：

- [ ] Prometheus + Grafana — 无 `micrometer-registry-prometheus` 依赖及指标埋点
- [ ] SkyWalking 链路追踪 — 无 Java Agent 配置（`traceId` 字段在 `Result<T>` 中存在但始终为 null）
- [ ] 结构化 JSON 日志（Logback JSON 格式）— 当前为默认文本格式

### 3.8 单元测试（全服务）❌

- [ ] 无任何 `src/test/` 目录
- [ ] 无 JUnit / Mockito 依赖
- [ ] 高优先级：秒杀 Lua 脚本单测、用户注册/登录单测、文件上传状态机单测

### 3.9 AI Agent（ai-bot）❌

- [ ] `ai-bot/` 目录存在但为空骨架，FastAPI 服务未实现
- [ ] RAG 检索、摘要推送、未读提醒聚合均未开始

---

## 四、文档与代码不一致项（已修复 / 待处理）

| 编号 | 文档描述 | 实际代码状态 | 风险 |
|------|---------|------------|------|
| **D-01** | API.md §D1：`presignedUrls` 应返回分片直传 URL 列表 | 代码始终返回 `[]` | 待 Phase 2 MinIO 集成 |
| **D-02** | API.md §D3：`merge` 返回 `fileUrl` 真实 OSS 地址 | 代码返回空字符串 | 待 Phase 2 MinIO 集成 |
| **D-03** | API.md §D1：`uploadId` 应为 MinIO 返回的真实 ID | 代码返回 `"TODO_MINIO_UPLOAD_ID"` | 待 Phase 2 MinIO 集成 |
| **D-04** | API.md §B4：`POST /clubs/{clubId}/announcements` 接口存在 | ✅ 已修复 | 已实现完整公告管理接口 |
| **D-05** | API.md §A4：`GET /me` 返回 `clubs` 数组 | 代码未实现跨服务查询 | 待 Phase 2 |
| **D-06** | campus-api：`UserFeignClient` 调用路径含 Gateway 前缀 | ✅ 已修复 | 路径已修正，端点已实现 |
| **D-07** | `ErrorCode` 码段与架构文档微小差异 | ✅ 已核对 | 码段正确，注释对齐 |
| **D-08** | API.md §C3：活动列表支持 `?clubId=&status=` 过滤 | ✅ 已修复 | `listActivities` 已添加过滤参数 |
| **D-09** | API.md §C1：Gateway Sentinel 限流（三层） | 无任何 Sentinel 配置 | 待 Phase 3 |

---

## 五、设计问题（已修复 / 待评审）

| 编号 | 问题描述 | 状态 | 处理结果 |
|------|---------|------|---------|
| **A-01** | `createClub` 使用 Query 参数传 `name/description/category`，破坏 REST 规范 | ✅ 已修复 | 改为 `@RequestBody CreateClubRequest`，添加 `@Valid` 参数校验 |
| **A-02** | 秒杀"先建订单再 Lua"存在幽灵订单风险 | ✅ 已修复 | 改为先 Lua 后建单，订单直接置 SUCCESS，Kafka 仅对账 |
| **A-03** | `UserFeignClient` 路径含 Gateway 前缀 `/user/`，Feign 直连时路径错误 | ✅ 已修复 | 路径改为 `/api/v1/users/{userId}/basic`，补全 ClubFeignClient/FileFeignClient |
| **A-04** | MySQL/MinIO 密码硬编码在 application.yml | ✅ 已修复 | 所有服务改为 `${ENV_VAR}` 引用，`.env.example` 补全模板 |
| **A-05** | IM `syncMessages` 全量 LIMIT 500，无会话配额均衡 | 📋 待 Phase 3 | 代码注释已标注，Phase 3 换 Kafka offset 方案 |
| **A-06** | 文件秒传仅校验 MD5，存在 Hash Flooding 风险 | ✅ 已修复 | 改为 MD5 + fileSize 双重校验 |
| **A-07** | `joinClub` 直接加入，与架构文档"社长审批入团"矛盾 | ✅ 已修复 | 改为申请待审批流程，新增 `club_member.status` 字段和审批接口 |

---

## 六、优先级路线图

### Phase 2（当前阶段，优先级最高）

- [ ] **[P0]** file-service：实现 `OssService`（MinIO SDK），修复 initUpload/merge 返回真实数据
- [ ] **[P0]** seckill-service：实现 Kafka Consumer，将订单从 PROCESSING 更新为 SUCCESS/FAILED
- [ ] **[P1]** club-service：实现公告管理（`createAnnouncement` / `listAnnouncements`），修复文档不一致 D-04
- [ ] **[P1]** user-service：新增内部接口 `GET /api/v1/internal/users/{userId}` 对齐 UserFeignClient
- [ ] **[P1]** seckill-service：`listActivities` 补充 `clubId` 和 `status` 过滤（修复 D-08）
- [ ] **[P2]** 修复设计问题 A-02（秒杀订单创建顺序）
- [ ] **[P2]** 敏感配置迁移至环境变量（修复 A-04）

### Phase 3（IM 系统）

- [ ] **[P0]** im-service：完整 WebSocket 实现（WsServer / WsSessionManager / 指令处理）
- [ ] **[P0]** im-service：Kafka Consumer 消息持久化
- [ ] **[P1]** Gateway：WebSocket 升级和 JWT 校验
- [ ] **[P1]** im-service：Redis Pub/Sub 跨节点消息分发
- [ ] **[P2]** im-service：已读回执 / 撤回 / 心跳
- [ ] **[P2]** Gateway：Sentinel 限流规则（修复 D-09 / A-09）

### Phase 4（智能化与质量）

- [ ] **[P1]** 单元测试：秒杀 Lua 脚本、用户鉴权、文件状态机
- [ ] **[P1]** 可观测性：Prometheus 埋点 + SkyWalking Agent + JSON 日志
- [ ] **[P2]** ai-bot：FastAPI AI Agent 骨架
- [ ] **[P2]** ClubFeignClient / FileFeignClient 完整实现
- [ ] **[P3]** 社团成员管理扩展（审批流 / 踢出 / 角色变更）

---

## 七、总体完成度快照

| 模块 | 完成度 | 主要缺口 |
|------|--------|---------|
| campus-common | **100%** | — |
| campus-gateway | **75%** | Sentinel 限流规则未配置 |
| campus-user-service | **85%** | 登出/改密/内部接口 未实现，/me clubs 字段缺失 |
| campus-club-service | **70%** | 公告管理完全缺失，成员管理有限 |
| campus-seckill-service | **75%** | Kafka Consumer 缺失（订单永远 PROCESSING），活动管理接口缺失 |
| campus-file-service | **45%** | MinIO 集成完全缺失，返回值均为占位符 |
| campus-im-service | **30%** | WebSocket / Kafka / ACK 全未实现 |
| campus-api | **40%** | UserFeignClient 调用方未实现对应端点，Club/File Feign 未定义 |
| ai-bot | **5%** | 骨架目录，无任何实现 |
| **整体** | **~65%** | 核心骨架完整，Phase 2-3 功能待落地 |
