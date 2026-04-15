# 后端任务总览与规划（重写版）

> 更新时间：2026-04-14  
> 范围：`campus-platform-backend/` + `docs/API.md` 对齐情况  
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
| `campus-file-service` | 骨架可用 | 秒传/断点逻辑可用；MinIO 真正上传链路未完成 |
| `campus-im-service` | REST 骨架可用 | 会话列表/离线同步已实现；WebSocket 主链路未做 |
| `ai-bot` | 未开始 | 目录存在但未形成可用服务 |

## 2.2 API 对齐结论（关键项）

| 项目 | 对齐情况 | 说明 |
|---|---|---|
| 用户 `GET /me` 返回 clubs | 已对齐 | user-service 已通过 ClubFeignClient 聚合社团信息 |
| 社团公告 `POST/GET /clubs/{clubId}/announcements` | 已对齐 | controller/service/mapper 均有实现 |
| 秒杀活动列表 `clubId/status` 过滤 | 已对齐 | `listActivities` 已支持过滤参数 |
| UserFeign 路径与 user-service 内部接口 | 已对齐 | `/api/v1/users/{userId}/basic` 一致 |
| 文件上传 `uploadId/presignedUrls/fileUrl` | 未对齐 | 仍为占位返回，依赖 MinIO 集成 |
| 秒杀“排队 + PROCESSING 状态机” | 已对齐 | 代码已改为 `PROCESSING` + Kafka Consumer 更新终态 |
| 网关 Sentinel 三层限流 | 未对齐 | 依赖存在，规则与拦截链未落地 |

---

## 3. 现阶段缺口与风险（Code Review 结论）

以下按优先级排序。

## 3.1 P0（高优先级）

1. IM 消息同步存在越权风险（指定会话时未校验会话成员关系）  
   影响：用户可通过构造 `conversationId` 拉取不属于自己的消息。

2. 文件上传合并接口可被伪造完成（未校验 `uploadId` 与 `fileMd5` 归属关系）  
   影响：可篡改上传流程状态，导致元数据与真实文件不一致。

3. 文件上传核心链路未完成（MinIO Multipart 未接入）  
   影响：`presignedUrls` 空、`uploadId` 占位、`fileUrl` 不可靠，客户端无法真正直传闭环。

## 3.2 P1（中优先级）

1. 网关缺少 C1 约定的限流防刷链路（Sentinel + 用户/IP 维度）  
   影响：高并发时防刷与保护能力不足。

2. 可观测性缺失（traceId 实际未注入、指标/追踪未闭环）  
   影响：线上排障成本高。

## 3.3 P2（工程质量）

1. 单测在当前环境不可通过（Mockito inline mock maker 依赖 attach 失败）  
   影响：`mvn test` 失败，不利于 CI 稳定。

2. 文档陈旧风险（历史评估与现状偏差）  
   影响：开发优先级被误导。

---

## 4. 开发规划（未来）

## Phase 2（近期：补齐可上线能力）

### P0

1. file-service 完成 MinIO 集成
- 实现 `OssService`（init multipart / presign / complete）
- `initUpload` 返回真实 `uploadId` 与 `presignedUrls`
- `merge` 返回真实 `fileUrl` 并做完整一致性校验

2. 修复 IM 同步越权
- `syncMessages(conversationId)` 增加 membership 校验
- 增加安全回归测试（合法会话/非法会话）

3. 修复上传合并归属校验
- 绑定 `uploadId ↔ fileMd5 ↔ uploaderId`
- 校验分片数量、etag 完整性、状态机流转合法性

### P1

1. Gateway 防刷
- 落地 Sentinel 规则
- 增加用户/IP 维度滑窗控制

2. 可观测性最小闭环
- traceId 注入与透传
- Prometheus 基础指标暴露

## Phase 3（IM 实时能力）

### P0

1. WebSocket 主链路
- `ws://gateway:9000/im/ws?token=...`
- 连接管理、消息分发、基础心跳

2. 消息可靠性
- ACK + 重试
- Kafka Consumer 持久化
- 基础幂等（客户端 msgId）

### P1

1. 跨节点消息路由
- Redis Pub/Sub（或等价方案）

2. 读回执/撤回
- `READ_REPORT` 落库
- 撤回时限校验与状态同步

## Phase 4（质量与智能化）

### P1

1. 测试体系修复与补齐
- 修复 Mockito Agent 配置（避免 inline attach 失败）
- 覆盖秒杀 Lua、文件状态机、核心鉴权流程

2. 日志与追踪
- JSON 结构化日志
- 关键业务链路埋点

### P2

1. `ai-bot` 骨架启动
- FastAPI 基础服务
- 与业务侧最小接口打通

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

## M3（Phase 4 完成）

- 核心模块测试稳定通过（可进 CI）
- 可观测性达到基本可运维水平
- ai-bot 提供最小可运行能力

---

## 6. 执行原则

1. 以“安全性与一致性”优先于“功能堆叠”。
2. 所有“文档/代码不一致”必须在同一迭代内收敛。  
3. 每个 P0 项必须附带最小回归测试与验收脚本。
