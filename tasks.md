# 后端任务总览与规划（重写版）

> 更新时间：2026-05-07  
> 范围：`campus-platform-backend/` + `ai-bot/` + `campus-platform-frontend/` + `docs/API.md` 对齐情况  
> 结论口径：以当前代码实现为准

---

## 1. 目标

本文件用于替代旧版评估，提供三类信息：

1. 当前后端真实实现进度（已完成）
2. 现阶段缺口与风险（待补齐）
3. 后续迭代规划（Phase 2/3/4）

---

## 2. 当前实现进度（以代码为准）

## 2.1 总体状态

| 模块 | 当前状态 | 备注 |
|---|---|---|
| `campus-common` | 基本完整 | 统一返回、异常、JWT、雪花 ID、MyBatis 填充已就位 |
| `campus-api` | 基本可用 | User/Club/File Feign 均已定义，含 fallback |
| `campus-gateway` | 可用（基础鉴权） | JWT 鉴权、白名单、路由可用；限流未落地 |
| `campus-user-service` | 功能较完整 | 注册/登录/刷新/me/登出/改密/内部用户查询已实现 |
| `campus-club-service` | 功能较完整 | 社团主流程 + 公告 + 成员管理 + 内部成员查询已实现 |
| `campus-seckill-service` | 核心链路可用 | Lua 原子扣减 + 报名 + 查询订单 + 活动筛选已实现；消费与管理能力不足 |
| `campus-file-service` | 可用 | 秒传/断点续传/全新上传全链路已通；MinIO 分片上传、预签名 URL、合并完成全部落地；merge 增加 MD5 绑定/分片完整性/状态机校验 |
| `campus-im-service` | 功能完整 | WebSocket 主链路、ACK 回执、Kafka 持久化、Redisson 跨节点路由、RECALL/READ_REPORT handler 均已落地 |
| `ai-bot` | Phase 4 P0 核心框架完成 | FastAPI + Claude agentic loop（MAX_TURNS=5）+ tool_registry + activity/club/user 三个工具 + 6 个 pytest；RAG/推荐引擎未实现 |
| `campus-platform-frontend` | 未开始（0%） | 仅有 package.json（React Native 0.79 + Zustand + Axios 依赖声明），无 src/ 源码 |

## 2.2 API 对齐结论（关键项）

| 项目 | 对齐情况 | 说明 |
|---|---|---|
| 用户 `GET /me` 返回 clubs | 已对齐 | user-service 已通过 ClubFeignClient 聚合社团信息 |
| 社团公告 `POST/GET /clubs/{clubId}/announcements` | 已对齐 | controller/service/mapper 均有实现 |
| 秒杀活动列表 `clubId/status` 过滤 | 已对齐 | `listActivities` 已支持过滤参数 |
| UserFeign 路径与 user-service 内部接口 | 已对齐 | `/api/v1/users/{userId}/basic` 一致 |
| 文件上传 `uploadId/presignedUrls/fileUrl` | 已对齐 | MinIO 分片上传链路已落地，`presignedUrls` 返回预签名 PUT URL 列表，`merge` 返回真实 `fileUrl` |
| 秒杀"排队 + PROCESSING 状态机" | 已对齐 | 代码已改为 `PROCESSING` + Kafka Consumer 更新终态 |
| 网关 Sentinel 三层限流 | 未对齐 | 依赖存在，规则与拦截链未落地 |

---

## 3. 现阶段缺口与风险（Code Review 结论）

以下按优先级排序。

## 3.1 P0（高优先级）

> ✅ Phase 2 P0 全部完成（2026-04-17）

| ID | 项目 | 状态 | 说明 |
|---|---|---|---|
| P0-1 | IM 消息同步越权 | ✅ 已修复 | `syncMessages(conversationId)` 增加 membership 校验，新增 4 个回归测试 |
| P0-2 | 文件上传合并归属校验 | ✅ 已修复 | `uploadId ↔ fileMd5` 交叉绑定校验 + 分片完整性检查 + 状态机流转校验 |
| P0-3 | MinIO 分片上传链路 | ✅ 已修复 | OssService 完整实现（init/presign/complete/abort），`initUpload` 返回真实预签名 URL，`merge` 返回真实 `fileUrl` |

## 3.2 P1（中优先级）

1. 网关缺少 C1 约定的限流防刷链路（Sentinel + 用户/IP 维度）  
   影响：高并发时防刷与保护能力不足。

2. 可观测性缺失（traceId 实际未注入、指标/追踪未闭环）  
   影响：线上排障成本高。

## 3.3 P2（工程质量）

1. 单测在当前环境不可通过（Mockito inline mock maker 依赖 attach 失败）  
   影响：`mvn test` 失败，不利于 CI 稳定。

2. ~~文档陈旧风险（历史评估与现状偏差）~~ — ✅ 已闭环

---

## 4. 开发规划（未来）

## Phase 2（近期：补齐可上线能力）

> ✅ **Phase 2 P0 全部完成**（2026-04-17）
> ✅ **Phase 2 P1 全部完成**（2026-04-30）

### P0 ✅ 已完成

1. ~~file-service 完成 MinIO 集成~~
   - ✅ 实现 `OssService`（init multipart / presign / complete / abort）
   - ✅ `initUpload` 返回真实 `uploadId` 与 `presignedUrls`（MinIO 预签名 PUT URL）
   - ✅ `merge` 返回真实 `fileUrl` 并做完整一致性校验

2. ~~修复 IM 同步越权~~
   - ✅ `syncMessages(conversationId)` 增加 membership 校验
   - ✅ 增加安全回归测试（合法会话/非法会话）

3. ~~修复上传合并归属校验~~
   - ✅ 绑定 `uploadId ↔ fileMd5 ↔ uploaderId`
   - ✅ 校验分片数量、etag 完整性、状态机流转合法性

### P1 ✅ 已完成

1. ~~Gateway 防刷~~
   - ✅ Sentinel 路由级 100 QPS 限流 + 秒杀 IP 20 QPS/登录 IP 5 QPS 防刷
   - ✅ 被限流返回 429 统一 JSON 格式

2. ~~可观测性最小闭环~~
   - ✅ traceId 全链路透传（Gateway 生成 → Header → MDC → 日志）
   - ✅ Prometheus 指标暴露（/actuator/prometheus + Prometheus 容器）

## Phase 3（IM 实时能力）

> ✅ **Phase 3 全部完成**（2026-05-06）

### P0 ✅ 已完成

1. ~~WebSocket 主链路~~
- ✅ `ws://gateway:9000/im/ws?token=...`，Gateway JwtAuthFilter 支持 query param 鉴权
- ✅ Spring WebSocket TextWebSocketHandler（`WsServer`）：连接建立/消息路由/断开清理
- ✅ `WsSessionManager`：userId ↔ WebSocketSession 线程安全双向映射
- ✅ `WsMessageDispatcher`：按 cmd 分发到 5 个 Handler（CHAT_MSG/HEARTBEAT/RECALL/READ_REPORT/TYPING）
- ✅ `WsHandshakeInterceptor`：握手阶段将 X-User-Id Header 写入 session attributes
- ✅ `ImNodeConfig`：nodeId 生成（hostname:port）、Redis 在线标识、Redisson RTopic 跨节点订阅

2. ~~消息可靠性~~
- ✅ 幂等去重：Redis `im:dedup:{msgId}` 5min TTL（setIfAbsent）
- ✅ ACK：服务端收到 CHAT_MSG 后立即回 ACK，含 serverMsgId 和状态
- ✅ Kafka Consumer（`MessagePersistConsumer`）：消费 `im-message-persist` 批量写 `im_message` 表
- ✅ 离线兜底：目标用户不在线时消息已入 Kafka，上线后 `/messages/sync` 拉取

### P1 ✅ 已完成

1. ~~跨节点消息路由~~
- ✅ Redisson RTopic Pub/Sub 跨节点推送

2. ~~读回执/撤回~~
- ✅ `READ_REPORT` Handler 落库
- ✅ `RECALL` Handler（撤回时限校验与状态同步）

## Phase 4（质量与智能化）

> ✅ **Phase 4 P0 ai-bot 核心框架完成**（2026-05-07）

### P0 ✅ 已完成

1. ~~ai-bot 骨架启动~~
- ✅ FastAPI 入口（`/health`、`/api/v1/chat`）
- ✅ Claude agentic loop（`campus_agent.py`，MAX_TURNS=5，tool_use/end_turn 分支）
- ✅ `tool_registry.py`：Tool 注册表与分发
- ✅ 3 个业务工具：`activity_tool`（秒杀活动查询）、`club_tool`（社团查询）、`user_tool`（当前用户信息）
- ✅ 6 个 pytest-asyncio 用例（end_turn / tool_use / MAX_TURNS / 异常场景）

### P1

1. ai-bot RAG 智能检索
   - `app/rag/embedder.py`：文档嵌入（向量化）
   - `app/rag/retriever.py`：向量检索（Milvus/Chroma）
   - 自动摘要（社团简介、活动详情向量化）

2. 智能推荐
   - 基于用户画像的社团/活动推荐引擎

3. 测试体系修复与补齐
- 修复 Mockito Agent 配置（避免 inline attach 失败）
- 覆盖秒杀 Lua、文件状态机、核心鉴权流程

4. 日志与追踪
- JSON 结构化日志
- 关键业务链路埋点

## Phase 5（前端 MVP — campus-platform-frontend）

> ✅ **Phase 5 Phase A 全部完成**（2026-05-07）

### Phase A ✅ 已完成（认证 + 布局骨架 + 社团列表）

1. ~~基础架构与用户认证~~
   - ✅ Next.js 16.2.5 + shadcn/ui + Tailwind CSS v4 工程初始化
   - ✅ Axios HTTP 客户端（连接 Gateway:9000，`/api/gateway` 代理转发）
   - ✅ Zustand v5 auth store（accessToken 内存 / refreshToken localStorage / cookie 同步给 proxy 路由保护）
   - ✅ 401 自动 token 刷新拦截器（pendingQueue 模式）
   - ✅ 登录/注册页面（Zod v4 表单验证）
   - ✅ `src/proxy.ts` 路由保护（未登录跳登录页；非 ADMIN 访问 /admin 跳首页）

2. ~~布局骨架~~
   - ✅ `(user)` layout：顶栏 + 侧边栏（Sidebar），role=2 显示管理入口
   - ✅ `admin` layout：AdminSidebar + 管理侧骨架页
   - ✅ 仪表盘首页（统计卡片占位）

3. ~~社团核心页面（Phase A 范围）~~
   - ✅ 社团列表 `/clubs`（TanStack Query + 搜索 + 分页 + ClubCard）
   - ✅ 社团详情页骨架 `/clubs/[id]`

### Phase B（社团管理 + 秒杀活动）— 待做

4. 社团详情完整实现
   - 成员列表、公告列表、活动 Tab
   - 申请加入社团按钮

5. 社团管理页 `/clubs/[id]/manage`
   - 成员审批（MEMBER_STATUS 0→1/2）、公告发布、角色变更

6. 秒杀活动列表 + 详情
   - 报名倒计时、名额进度条
   - 报名流程（乐观 UI + 轮询订单状态）

### Phase C（IM 即时通讯）— 待做

7. wsClient.ts 封装（心跳/重连/消息队列）
8. Zustand imStore（conversations / messages / unreadCount）
9. 会话列表 + 聊天界面（MessageBubble / 虚拟列表）
10. ACK 状态指示 / 离线消息拉取 / 撤回 / 已读回执

### Phase D（文件上传 + AI 助手）— 待做

11. Web Worker MD5 + 分片上传（3 并发、断点续传）
12. AI 助手页（打字机流式效果 + 对话历史）

### Phase E（管理侧）— 待做

13. Admin 数据大盘（StatsCard + Recharts）
14. 社团审核页（Pending 队列 + ReviewDialog）
15. 用户管理页（DataTable + 启用/禁用）[依赖新后端接口]
16. 活动/订单看板（折线图）[依赖新统计接口]
17. 系统配置占位页

---

## 5. 里程碑验收标准

## M1（Phase 2 完成）

- 文件上传可真实完成：前端可拿到可用 presigned URL 并成功 merge
- IM 同步与上传合并越权问题关闭
- 秒杀文档与代码语义一致
- 网关具备基础限流防刷能力

## M2（Phase 3 完成）

- IM WebSocket 可用，支持实时收发、ACK、基础重试
- 跨节点消息路由可用
- 已读回执与撤回可用

## M3（Phase 4 P0 完成）

- ai-bot 提供可用 `/api/v1/chat` 接口，agentic loop 可查询活动/社团/用户信息
- 6 个单元测试通过
- RAG/推荐引擎为 Phase 4 P1 待做项

## M4（Phase 5 前端 MVP 完成）

- 用户可完成注册/登录/查看社团/加入社团完整流程
- IM 实时聊天可用（WebSocket 收发、ACK、离线拉取）
- 文件上传可用（分片 + 秒传）
- 所有功能连接真实 Gateway API，非 mock

## M4-A（Phase 5 Phase A 完成）✅ 2026-05-07

- `pnpm build` 通过，无 TypeScript 错误，无废弃警告
- 登录/注册页可用，accessToken 存入 store，refreshToken 持久化
- proxy 路由保护生效（未登录跳登录页，非 ADMIN 访问 /admin 跳首页）
- 社团列表从真实 API 加载，分页/搜索正常
- 用户侧 / 管理侧布局分区完成

---

## 6. 执行原则

1. 以“安全性与一致性”优先于“功能堆叠”。
2. 所有“文档/代码不一致”必须在同一迭代内收敛。  
3. 每个 P0 项必须附带最小回归测试与验收脚本。
