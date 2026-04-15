# 接口规范 (API.md)

> 所有接口通过 **Gateway（端口 9000）** 统一访问，无需直连各业务服务。

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
    private String traceId; // SkyWalking 链路追踪 ID
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

**处理流程：**
```
Client → Gateway(Sentinel限流) → 防刷拦截 → Redis Lua(原子扣减) → Kafka(异步发送) → 返回"排队中"
```

**限流策略：**

| 层级 | 策略 | 配置 |
|------|------|------|
| Gateway | Sentinel 令牌桶 | 单接口 QPS ≤ 1000 |
| 用户维度 | 滑动窗口 | 同一用户 5 秒内 ≤ 1 次 |
| IP 维度 | 滑动窗口 | 同一 IP 1 秒内 ≤ 10 次 |

**Response（正常排队）：**
```json
{
  "code": 200,
  "msg": "报名排队中，请轮询 /api/v1/orders/{orderId} 查看结果",
  "data": {
    "orderId": "987654321098765432"
  }
}
```

> 报名后内部流程：预创建 PROCESSING 订单 → Redis Lua 原子扣减 → 扣减成功发 Kafka 异步更新为 SUCCESS；若 Lua 失败（重复报名/库存不足/活动未预热），则回滚预创建订单并返回对应错误码。客户端拿到 `orderId` 后可通过 `GET /api/v1/orders/{orderId}` 轮询最终状态。

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

**订单状态机：**

```mermaid
stateDiagram-v2
    [*] --> PROCESSING: 提交报名
    PROCESSING --> SUCCESS: Kafka消费成功
    PROCESSING --> FAILED: 库存不足/重复报名/异常
    SUCCESS --> CANCELLED: 用户取消
    FAILED --> [*]
    CANCELLED --> [*]
```

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

> 注：Phase 2 将统一改为 Request Body JSON 传参，当前使用 Query 参数。

**Response（需要上传）：**
```json
{
  "code": 200,
  "data": {
    "type": "new",
    "uploadId": "TODO_MINIO_UPLOAD_ID",
    "presignedUrls": []
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

**上传流程：**
```mermaid
sequenceDiagram
    participant C as Client
    participant F as file-service
    participant R as Redis
    participant O as OSS

    C->>C: 1. 计算文件 MD5
    C->>F: 2. POST /upload/init
    F->>F: 3. 查 file_meta 表
    alt 文件已存在（秒传）
        F-->>C: 返回 {type: "instant", fileUrl}
    else 文件不存在
        F->>O: 4. 创建 Multipart Upload
        F->>R: 5. 记录分片进度
        F-->>C: 返回分片预签名 URL 列表
        loop 每个分片
            C->>O: 6. PUT 分片到预签名 URL
            C->>F: 7. POST /upload/chunk/complete
        end
        C->>F: 8. POST /upload/merge
        F->>O: 9. Complete Multipart Upload
        F-->>C: 返回最终 fileUrl
    end
```

**文件上传限制：**

| 配置 | 值 |
|------|-----|
| 单文件最大 | 2 GB |
| 分片大小 | 5 MB（弱网建议 2 MB） |
| 客户端并发分片数 | 3 |
| 预签名 URL 有效期 | 1 小时 |
| 分片记录 TTL | 24 小时 |

---

### D2. 上报分片完成

**`POST /file/api/v1/upload/chunk/complete`** — 需鉴权

**Query 参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `uploadId` | String | 是 | 分片上传 ID |
| `partNumber` | Int | 是 | 分片序号（从 1 开始） |
| `etag` | String | 是 | 分片 ETag |

> 注：Phase 2 将统一改为 Request Body JSON 传参。

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

> 注：Phase 2 将统一改为 Request Body JSON 传参；届时 `fileMd5` 由服务端从 uploadId 关联的 Redis 记录中自动获取，客户端只需传 `uploadId`。

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

> Phase 3 将补充 `limit`（分页大小）、`direction`（拉取方向）参数，并换为 Kafka offset 方案。

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

> 注：当前返回为 Entity 列表（骨架阶段）。Phase 3 将封装为 DTO，增加 `senderName`、`senderAvatar` 等冗余字段，返回结构为 `{ hasMore, messages }`。

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

> 注：Phase 3 WebSocket 实现后将补充 `lastMessage`、`unreadCount`、`muted`、`peerUser` 等字段，当前仅返回会话基础信息。

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
| `ACK` | Server → Client | 消息接收确认 | 服务端处理完成 |
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

### 7.4 WebSocket 生命周期时序图

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as Gateway
    participant IM as im-service
    participant R as Redis
    participant K as Kafka

    C->>GW: 1. WebSocket Handshake (JWT)
    GW->>GW: 2. 校验 JWT
    GW->>IM: 3. 路由到 im-service 节点
    IM->>R: 4. SET im:online:{userId} = nodeId
    IM-->>C: 5. 连接建立成功

    loop 心跳 (30s)
        C->>IM: HEARTBEAT
        IM-->>C: HEARTBEAT
    end

    C->>IM: 6. CHAT_MSG (msgId=C-12345)
    IM->>IM: 7. 校验 msgId 唯一性（幂等）
    IM-->>C: 8. ACK (refMsgId=C-12345, serverMsgId=S-99999)
    IM->>K: 9. 异步投递消息（partition by conversationId）

    alt 接收方在同一节点
        IM-->>C: 10a. PUSH_MSG
    else 接收方在其他节点
        IM->>R: 10b. Pub/Sub → im:node:{targetNode}
        R-->>IM: 目标节点收到消息
        IM-->>C: 10c. PUSH_MSG
    end

    K->>IM: 11. Consumer 消费 → 批量写入 im_message
```

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
