# 接口规范 (API.md)

> 修订日期：2026-09-05。§1–7 记录当前 v1 接口与已识别限制，§8 为待实现 v2 契约。当前行为来自静态代码核对，完整运行验收见 tasks.md。
> 业务接口通过 Gateway（端口 9000）访问。

---

## 1. 全局约定

### 1.1 基础 URL 格式

```
http(s)://gateway:9000/{service-prefix}/api/v1/{resource}
```

### 1.2 统一响应结构

```java
// campus-common: Result<T>
public class Result<T> {
    private int code;       // 业务状态码
    private String msg;     // 提示信息
    private T data;         // 业务数据载荷
    private String traceId; // 请求关联 ID，具体注入与日志传播由链路实现负责
}
```

**响应示例：**
```json
{
  "code": 200,
  "msg": "Success",
  "data": { "...": "..." },
  "traceId": "T-a1b2c3d4e5f6"
}
```

### 1.3 认证方式

需要登录的接口在 Header 中携带：
```
Authorization: Bearer <accessToken>
```

### 1.4 业务状态码

| 状态码 | 含义 | HTTP Status |
|-------|------|------------|
| `200` | 请求成功 | 200 |
| `400` | 参数校验失败 | 400 |
| `401` | 未登录或 Token 过期 | 401 |
| `403` | 无操作权限 | 403 |
| `404` | 资源不存在 | 404 |
| `429` | 请求频率超限 | 429 |
| `500` | 系统内部异常 | 500 |
| `5001` | 活动名额已满（库存不足） | 200 |
| `5002` | 重复报名 | 200 |
| `5003` | 活动未开始 | 200 |
| `5004` | 活动已结束 | 200 |
| `5005` | 活动已取消 | 200 |
| `1041-1044` | 文件业务错误（不存在/过大/上传失败） | 200 |
| `1021-1024` | IM 业务错误 | 200 |
| `1011-1015` | 社团业务错误 | 200 |

### 1.5 分页约定

分页响应 `data` 格式：
```json
{
  "total": 42,
  "pages": 3,
  "current": 1,
  "records": [ "..." ]
}
```

分页请求 Query 参数：`?page=1&size=20`

---

## 2. 用户服务 API（`/user/api/v1/...`）

### A1. 用户注册

**`POST /user/api/v1/register`** — 无需鉴权

**Request Body：**
```json
{
  "username": "zhangsan",
  "password": "Abc@123456",
  "email": "zhangsan@campus.edu",
  "nickname": "张三"
}
```

**参数校验：**

| 字段 | 规则 |
|------|------|
| `username` | 必填，4-64字符，字母数字下划线 |
| `password` | 必填，8-128字符，含大小写+数字+特殊字符 |
| `email` | `@Email`，可选 |
| `nickname` | 2-64字符，可选 |

**Response (201)：**
```json
{
  "code": 200,
  "msg": "注册成功",
  "data": {
    "userId": "1780001234567890",
    "username": "zhangsan"
  }
}
```

---

### A2. 用户登录

**`POST /user/api/v1/login`** — 无需鉴权

**Request Body：**
```json
{
  "username": "zhangsan",
  "password": "Abc@123456"
}
```

**Response：**
```json
{
  "code": 200,
  "msg": "登录成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1Ni...",
    "refreshToken": "eyJhbGciOiJIUzI1Ni...",
    "expiresIn": 7200,
    "userInfo": {
      "userId": "1780001234567890",
      "username": "zhangsan",
      "nickname": "张三",
      "role": 0,
      "avatarUrl": "https://oss.example.com/avatar/default.png"
    }
  }
}
```

**Token 策略：**

| Token 类型 | 有效期 | 存储位置 |
|-----------|--------|---------|
| `accessToken` | 2 小时 | 客户端内存 / SecureStorage |
| `refreshToken` | 7 天 | Redis + 客户端 SecureStorage |

---

### A3. 刷新 Token

**`POST /user/api/v1/token/refresh`** — 无需鉴权

**Request Body：**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1Ni..."
}
```

**Response：**
```json
{
  "code": 200,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1Ni...(new)",
    "expiresIn": 7200
  }
}
```

---

### A4. 获取当前用户信息

**`GET /user/api/v1/me`** — 需鉴权

**Response：**
```json
{
  "code": 200,
  "data": {
    "userId": "1780001234567890",
    "username": "zhangsan",
    "nickname": "张三",
    "email": "zhangsan@campus.edu",
    "role": 0,
    "avatarUrl": "https://oss.example.com/avatar/xxx.png",
    "clubs": [
      { "clubId": "100001", "clubName": "编程社", "memberRole": 0 },
      { "clubId": "100002", "clubName": "摄影社", "memberRole": 2 }
    ]
  }
}
```

---

## 3. 社团服务 API（`/club/api/v1/...`）

### B1. 创建社团

**`POST /club/api/v1/clubs`** — 需鉴权

**Query 参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | String | 是 | 社团名称 |
| `description` | String | 否 | 社团简介 |
| `category` | String | 否 | 分类（如"学术""文艺""体育"） |

> 注：Phase 2 将改为 Request Body JSON 传参，以支持 `logoFileId` 等更多字段。

**Response：**
```json
{
  "code": 200,
  "msg": "社团创建成功，待审核",
  "data": {
    "clubId": "200001"
  }
}
```

---

### B2. 社团列表

**`GET /club/api/v1/clubs`** — 无需鉴权

**Query：** `?category=学术&page=1&size=20`

> 注：Phase 2 将支持 `keyword` 模糊搜索（按社团名称）。

**Response：**
```json
{
  "code": 200,
  "data": {
    "total": 42,
    "pages": 3,
    "current": 1,
    "records": [
      {
        "id": "100001",
        "name": "编程社",
        "description": "热爱代码的同学聚集地",
        "logoUrl": "https://oss.example.com/club/logo1.png",
        "leaderId": "1780001234567890",
        "category": "学术",
        "status": 1,
        "memberCount": 156,
        "createTime": "2026-03-01T10:00:00",
        "updateTime": "2026-04-01T12:00:00"
      }
    ]
  }
}
```

> 注：Phase 2 将使用 DTO 层封装，隐藏 `id`、`status` 等内部字段，补充 `leaderName`（通过 UserFeignClient 跨服务查询）。

---

### B3. 申请加入社团

**`POST /club/api/v1/clubs/{clubId}/join`** — 需鉴权

**Request Body：** 无

> 注：Phase 2 将支持 `reason` 字段，通过 Request Body 传入入社团申请说明。

**Response：**
```json
{
  "code": 200
}
```

---

### B4. 发布公告

**`POST /club/api/v1/clubs/{clubId}/announcements`** — 需鉴权（社长/副社长）

**Request Body：**
```json
{
  "title": "本周六技术分享会",
  "content": "## 主题\nSpring Cloud 微服务实战\n\n## 时间\n4月15日 14:00-16:00",
  "isPinned": true
}
```

**Response：**
```json
{
  "code": 200,
  "data": {
    "announcementId": "300001",
    "title": "本周六技术分享会"
  }
}
```

---

## 4. 秒杀系统 API（`/seckill/api/v1/...`）

### C1. 秒杀报名 ⚡ 核心高频接口

**`POST /seckill/api/v1/activities/{activityId}/book`** — 需鉴权

**当前实现：**

`查询并校验活动 → 检查已有订单 → MySQL 条件扣减 available_stock → 插入 SUCCESS 订单 → 同一事务提交`

Redis、Lua 和 Kafka 不再位于报名成功边界。条件更新要求活动仍可报名且 `available_stock > 0`；`uk_user_activity` 处理并发重复请求，插入冲突会使本次库存扣减随事务回滚。当前重复报名返回业务码 5002，不返回原订单。

**Response（成功）：**

```json
{
  "code": 200,
  "msg": "报名成功",
  "data": { "orderId": "987654321098765432" }
}
```

当前限流配置：每个 Gateway 实例按服务路由 100 请求/s，秒杀路由同 IP 20 请求/s，用户路由同 IP 30 请求/s。用户维度和登录接口分组规则待实现。

**Response（库存不足）：**
```json
{ "code": 5001, "msg": "活动名额已满", "data": null }
```

**Response（重复报名）：**
```json
{ "code": 5002, "msg": "您已报名该活动，请勿重复操作", "data": null }
```

---

### C2. 查询订单结果

**`GET /seckill/api/v1/orders/{orderId}`** — 需鉴权

**Response（成功）：**
```json
{
  "code": 200,
  "data": {
    "id": "987654321098765432",
    "userId": 1780001234567890,
    "activityId": "500001",
    "status": 1,
    "cancelReason": null,
    "createTime": "2026-04-12T15:00:01",
    "updateTime": "2026-04-12T15:00:01"
  }
}
```

> 权限控制：若订单不属于当前登录用户（userId 不匹配），返回 `403 FORBIDDEN`。

**当前状态：** 当前 book 在同一数据库事务中扣库存并写 `status=1`，提交成功即表示报名成功；异步 Consumer 已删除。取消接口尚未实现。

---

### C3. 活动列表

**`GET /seckill/api/v1/activities`** — 无需鉴权

**Query：** `?clubId=100001&status=1&page=1&size=10`

**Response：**
```json
{
  "code": 200,
  "data": {
    "total": 5,
    "pages": 1,
    "current": 1,
    "records": [
      {
        "id": "500001",
        "clubId": "100001",
        "title": "2026校园音乐节",
        "description": null,
        "coverUrl": "https://oss.example.com/activity/music.png",
        "location": "大礼堂",
        "activityTime": "2026-04-20T19:00:00",
        "totalStock": 500,
        "availableStock": 123,
        "startTime": "2026-04-15T12:00:00",
        "endTime": "2026-04-18T23:59:59",
        "status": 1,
        "createTime": "2026-04-10T10:00:00",
        "updateTime": "2026-04-10T10:00:00"
      }
    ]
  }
}
```

> 注：Phase 2 将使用 DTO 层封装，隐藏 `id`、`status` 等内部字段，补充 `clubName`（通过 ClubFeignClient 跨服务查询）。

---

## 5. 文件系统 API（`/file/api/v1/...`）

### D1. 初始化分片上传

**`POST /file/api/v1/upload/init`** — 需鉴权

**Query 参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `fileName` | String | 是 | 原始文件名 |
| `fileSize` | Long | 是 | 文件大小（字节） |
| `fileMd5` | String | 是 | 文件 MD5 哈希（32 位） |
| `chunkCount` | Int | 否 | 分片总数，默认 1 |

> 当前使用 Query 参数；目标 v2 使用 JSON Body，见 §8.2。工作区已将 Multipart 参数放入签名计算并通过单测；真实 PUT/ListParts/Complete/GET 仍因 Docker 引擎故障待验证。

**Response（当前返回结构，URL 为格式示意）：**
```json
{
  "code": 200,
  "data": {
    "type": "new",
    "uploadId": "d41d8cd98f00b204e9800998ecf8427e:1001",
    "presignedUrls": ["https://storage.example.com/bucket/object?signature=example"],
    "minioUploadId": "provider-upload-id"
  }
}
```

**Response（秒传命中）：**
```json
{
  "code": 200,
  "data": {
    "type": "instant",
    "fileUrl": "https://oss.example.com/files/a1b2c3d4.mp4"
  }
}
```

**Response（断点续传）：**
```json
{
  "code": 200,
  "data": {
    "type": "resume",
    "uploadedParts": ["1", "2", "3"]
  }
}
```

**当前限制：**

- 秒传按 MD5 和大小返回已有 URL，业务授权需按目标设计补齐。
- 当前续传读取 `file:chunk:{fileId}`，上报写入 `file:chunk:{uploadId}`，且续传响应缺少任务 ID 和可刷新 URL。
- 存储初始化、分片 PUT、合并与最终下载需串联验证；接口返回 URL 的单测不能证明上传成功。
- 目标限制为单文件 ≤ 2 GiB、默认 8 MiB 分片、并行 3 片；S3/MinIO 非末片至少 5 MiB，URL 1 小时、任务 24 小时。当前代码的参数校验完整性需按任务清单补齐。

---

### D2. 上报分片完成

**`POST /file/api/v1/upload/chunk/complete`** — 需鉴权

**Query 参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `uploadId` | String | 是 | 分片上传 ID |
| `partNumber` | Int | 是 | 分片序号（从 1 开始） |
| `etag` | String | 是 | 分片 ETag |

> 当前使用 Query 参数；目标 v2 见 §8.2。

**Response：**
```json
{
  "code": 200
}
```

---

### D3. 合并文件

**`POST /file/api/v1/upload/merge`** — 需鉴权

**Query 参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `fileMd5` | String | 是 | 文件 MD5（即 fileId，用于定位 file_meta 记录） |
| `uploadId` | String | 是 | 分片上传 ID |

> 当前校验上传者与 MD5 绑定，分片完整性只比较条目数量；重复合并和存储已完成后的恢复仍待补齐。目标 v2 从上传任务取得元数据，见 §8.2。

**Response：**
```json
{
  "code": 200,
  "data": {
    "fileUrl": "https://oss.example.com/files/a1b2c3d4.mp4"
  }
}
```

---

## 6. IM 系统 REST API（`/im/api/v1/...`）

### E1. 离线消息拉取

**`GET /im/api/v1/messages/sync`** — 需鉴权

**Query 参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `conversationId` | String | 否 | 指定会话 ID；不传则拉取所有会话的近期消息（全局 LIMIT 500） |
| `lastMsgId` | String | 否 | 上次已收到的最新消息 ID，不传则从最早开始 |

> 当前指定会话时用 `msgId > lastMsgId` 过滤、按 createTime/msgId 排序并 LIMIT 100；未指定会话时忽略 lastMsgId，按时间返回最早 500 条。该行为尚不具备完整分页恢复能力。目标 v2 使用会话 seq，见 §8.3。

**Response：**
```json
{
  "code": 200,
  "data": [
    {
      "msgId": "S-888889",
      "conversationId": "CONV_G_100001",
      "senderId": 1780001234567890,
      "msgType": 1,
      "content": "{\"text\": \"明天几点集合？\"}",
      "atUserIds": null,
      "replyMsgId": null,
      "isRecalled": 0,
      "createTime": "2026-04-12T14:30:00",
      "updateTime": "2026-04-12T14:30:00"
    }
  ]
}
```

> 当前返回 Entity 列表；目标 v2 返回 messages、nextSeq、hasMore。

---

### E2. 获取会话列表

**`GET /im/api/v1/conversations`** — 需鉴权

**Response：**
```json
{
  "code": 200,
  "data": [
    {
      "conversationId": "CONV_G_100001",
      "type": 2,
      "name": "编程社群聊",
      "avatarUrl": "https://oss.example.com/club/logo1.png",
      "ownerId": "1780001234567890",
      "maxMembers": 500,
      "createTime": "2026-04-01T10:00:00",
      "updateTime": "2026-04-12T14:30:00"
    },
    {
      "conversationId": "CONV_P_1001_1002",
      "type": 1,
      "name": null,
      "avatarUrl": null,
      "ownerId": null,
      "maxMembers": null,
      "createTime": "2026-04-05T08:00:00",
      "updateTime": "2026-04-12T10:00:00"
    }
  ]
}
```

> 当前仅返回会话基础信息；最近消息、未读数和社团会话映射按目标设计补充。

---

## 7. WebSocket 协议

### 7.1 连接建立

```
ws://gateway:9000/im/ws?token=<JWT>
```

> Gateway 在 WebSocket 握手阶段校验 JWT，合法后路由到 im-service 节点。

### 7.2 指令集定义

| 指令（`cmd`） | 方向 | 说明 | 触发时机 |
|-------------|------|------|---------|
| `CHAT_MSG` | Client → Server | 发送聊天消息 | 用户发消息 |
| `ACK` | Server → Client | 数据库单条消息插入成功后返回 | 包含真实 serverMsgId；重复 clientMsgId 返回首次保存的 ID |
| `PUSH_MSG` | Server → Client | 推送新消息 | 有新消息到达 |
| `HEARTBEAT` | 双向 | 心跳保活 | 每 30s |
| `RECALL` | Client → Server | 撤回消息 | 2 分钟内可撤回 |
| `READ_REPORT` | Client → Server | 已读回执上报 | 用户阅读消息后 |
| `TYPING` | Client → Server | 正在输入状态 | 用户输入中 |
| `KICK_OFF` | Server → Client | 强制下线 | 其他设备登录/封号 |

### 7.3 消息格式

```json
// 客户端发送消息
{
  "cmd": "CHAT_MSG",
  "msgId": "C-12345",
  "payload": {
    "conversationId": "CONV_G_100001",
    "type": 1,
    "content": "{\"text\": \"明天几点集合？\"}",
    "atUserIds": [],
    "replyMsgId": null
  }
}

// 服务端确认回执
{
  "cmd": "ACK",
  "refMsgId": "C-12345",
  "payload": {
    "serverMsgId": "S-99999",
    "timestamp": 1680000000000,
    "status": "OK"
  }
}

// 服务端推送新消息
{
  "cmd": "PUSH_MSG",
  "payload": {
    "msgId": "S-99999",
    "conversationId": "CONV_G_100001",
    "senderId": 1780001234567890,
    "senderName": "张三",
    "senderAvatar": "https://oss.example.com/avatar/xxx.png",
    "type": 1,
    "content": "{\"text\": \"明天几点集合？\"}",
    "timestamp": 1680000000000
  }
}

// 心跳（双向）
{
  "cmd": "HEARTBEAT",
  "timestamp": 1680000030000
}

// 撤回消息
{
  "cmd": "RECALL",
  "payload": {
    "conversationId": "CONV_G_100001",
    "msgId": "S-99999"
  }
}

// 已读回执
{
  "cmd": "READ_REPORT",
  "payload": {
    "conversationId": "CONV_G_100001",
    "lastReadMsgId": "S-99999"
  }
}
```

### 7.4 当前生命周期与限制

`握手认证 → 成员校验 → 查询 (senderId,clientMsgId) → 数据库插入新消息 → ACK → 实时推送`

当前成功 ACK 晚于单条数据库插入；插入失败不发送 ACK/推送。`(sender_id,client_msg_id)` 唯一约束吸收并发重试并返回原 serverMsgId。当前仍无会话 seq/cursor、接收设备 DELIVERY_ACK 和完整重试队列，重复 clientMsgId 携带不同会话或内容时也尚未返回幂等冲突；这些缺口见 §8.3。

---

## 8. 后续接口扩展（待实现）

本节定义 Plan 1 尚未完成的接口。新增不兼容能力时使用 `/{service}/api/v2/...` 或显式 WebSocket 协议协商；当前 v1 报名同步终态继续作为基线。ID、seq、事件游标统一为 JSON 字符串，鉴权主体从可信服务端上下文读取。

### 8.1 报名与活动管理

| 当前/候选接口 | 请求 | 响应与语义 |
|---|---|---|
| `POST /seckill/api/v1/activities/{activityId}/book` | 无 body，需鉴权 | MySQL 事务提交后 HTTP 200，`data={orderId}`；库存不足或重复报名返回登记业务码 |
| `GET /seckill/api/v1/orders/{orderId}` | 需本人身份 | 返回已提交订单；非本人 403，不存在 404 |
| `POST /seckill/api/v2/activities` | 活动草稿字段，需社团管理权限 | 创建草稿并返回字符串 activityId |
| `PATCH /seckill/api/v2/activities/{activityId}` | 可编辑字段 | 只允许在受理开始前修改，服务端校验所属社团管理权限 |
| `POST /seckill/api/v2/activities/{activityId}/publish` | `{}` | 校验时间和名额后发布；发布后当前同步报名接口可用 |
| `POST /seckill/api/v2/activities/{activityId}/cancel` | `{reason}` | 停止新报名；已有成功订单如何处理需单独确认，不在接口层静默回补 |

异步 202/PROCESSING、Redis 预占、Stream 和 Kafka 削峰不是已确定的 v2 目标。只有 Plan 1 验收后，固定资源下的单库容量低于真实需求时，才按 [优化与演进 O04](优化与演进.md) 另立方案并重新定义兼容契约。

### 8.2 文件任务与下载

| 接口 | JSON 请求/参数 | 响应 data |
|---|---|---|
| `POST /file/api/v2/uploads` | `{fileName,fileSize,sha256,bizType,bizId,sourceReferenceId?}` | `{type,uploadId,state,partSize,partCount,uploadedParts,parts,expiresAt}`；有权复用时返回 `{type:"instant",fileId,referenceId}` |
| `GET /file/api/v2/uploads/{uploadId}` | 上传者身份 | 任务状态与存储端已完成分片；COMPLETED 返回同一 fileId/referenceId |
| `POST /file/api/v2/uploads/{uploadId}/parts/presign` | `{partNumbers:[1,2]}` | `{parts:[{partNumber,url,requiredHeaders,expiresAt}]}`，仅任务有效且有权时签发 |
| `POST /file/api/v2/uploads/{uploadId}/parts/complete` | `{partNumber,etag}` | 缓存进度；最终以存储 ListParts 核验 |
| `POST /file/api/v2/uploads/{uploadId}/complete` | `{}` | 完成/校验处理中 HTTP 202 `{uploadId,state}`；已完成 HTTP 200 `{fileId,referenceId,state:"COMPLETED"}` |
| `POST /file/api/v2/uploads/{uploadId}/abort` | `{}` | 幂等终止未完成任务，并清理存储 Multipart |
| `GET /file/api/v2/references/{referenceId}/download-url` | 当前身份 | 授权后 `{url,expiresAt}`，GET URL 有效期 5 分钟 |

bizType 支持已有业务对象；未发布消息附件先使用 `CONVERSATION_ASSET` 与 conversationId，消息提交后创建 IM_MSG 引用。fileSize 为非负整数且不超过服务端上限；空文件走单对象上传路径。服务端决定 partSize/count，分片号范围和实际长度均校验。初始化时校验目标业务写权限；完成时再次校验，权限已撤销则不建立业务引用并清理临时对象。sourceReferenceId 为秒传授权依据，验证读取资格及目标写权限后才能复用。

重复 complete 返回同一结果，进程中断后依据独占 objectKey 与存储元数据恢复。COMPLETING/VERIFYING 由后台恢复任务推进；过期或已终止任务返回 410，仍在初始化/合并时返回可轮询状态。签名 URL、原文件内容和访问凭证不写入业务日志。

### 8.3 IM 保存、分页与接收确认

| 接口/指令 | 字段 | 语义 |
|---|---|---|
| v2 `CHAT_MSG` | `{msgId:clientMsgId,payload:{conversationId,type,content,...}}` | 校验身份、成员资格与幂等；事务写消息、seq 和 Outbox |
| v2 `ACK` | `{refMsgId,payload:{status:"OK",serverMsgId,seq,timestamp}}` | DB 提交后返回；重复请求返回同一 msgId/seq |
| v2 `PUSH_MSG` | `{payload:{msgId,conversationId,seq,senderId,type,content,...}}` | 按 seq 去重显示；缺口通过 REST 补齐 |
| v2 `DELIVERY_ACK` | `{payload:{conversationId,receivedSeq}}` | Client→Server；会话内连续接收位置，绑定当前 sessionId |
| v2 `READ_REPORT` | `{payload:{conversationId,readSeq}}` | 单调推进，校验不超过可见消息上限 |
| `GET /im/api/v2/messages/sync` | 必填 conversationId，afterSeq 默认 "0"，limit 默认 100、最大 200 | `{messages,nextSeq,hasMore}`，严格 `seq > afterSeq ORDER BY seq ASC` |
| `GET /im/api/v2/conversations` | 当前身份，分页参数 | 会话列表含 clubId、lastSeq、readSeq、latestEventCursor、unreadCount；用于发现新消息与变更 |
| `GET /im/api/v2/conversations/{id}/events` | afterCursor 默认 "0"、limit 最大 200 | `{events,nextCursor,hasMore}`，覆盖新消息与撤回等提交事件 |

DELIVERY_ACK 校验收到的 seq 属于当前会话且未超过服务端已提交上限，不能推进用户已读位置。消息正文暂不可读取时仍返回可见消息的占位记录及 seq，避免游标因撤回产生永久缺口；撤回事件对既有消息做原位更新。

会话消息 seq 与事件 cursor 在事务中分配并随提交对外可见，排序、过滤和分页使用同一字段。初次同步和重连先取得会话快照，再将 REST 补拉与实时流按 seq/cursor 合并；定期核对 lastSeq 与 latestEventCursor，覆盖末条推送丢失和离线撤回。

相同 senderId/clientMsgId 对应不同内容返回 HTTP/协议错误 `IDEMPOTENCY_CONFLICT`（目标业务码 409）；未授权返回 403，依赖暂时不可用返回可重试错误。客户端只有收到成功 ACK 才显示“已发送”，发送中断保留原 clientMsgId。

---

## 附录：全局异常码速查表

| 码段 | 范围 | 所属服务 |
|------|------|---------|
| `200` | 成功 | 全局 |
| `400-499` | 客户端错误 | 全局 |
| `500` | 系统内部错误 | 全局 |
| `1001-1010` | 用户业务错误 | user-service |
| `1011-1020` | 社团业务错误 | club-service |
| `1021-1030` | IM 业务错误 | im-service |
| `1041-1050` | 文件业务错误 | file-service |
| `5001-5010` | 秒杀业务错误 | seckill-service |
