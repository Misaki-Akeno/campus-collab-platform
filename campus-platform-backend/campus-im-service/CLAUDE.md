# campus-im-service 开发指南

## 职责

IM 消息系统（端口 **8083**）：WebSocket 长连接管理、消息路由分发、ACK 重试机制、离线消息同步、已读回执。**系统中唯一的有状态服务**（持有 WS 连接）。

## 技术栈

Spring WebMVC + JDK 21 虚拟线程（WebSocket over Tomcat）
Redis + Kafka + MySQL + MyBatis-Plus

## 核心 Redis Key

| Key 模板 | 结构 | TTL | 说明 |
|---------|------|-----|------|
| `im:online:{userId}` | String | WS 连接同生命周期 | value = nodeId |
| `im:unread:{userId}` | Hash | 永久 | field=conversationId, value=count |
| `im:dedup:{msgId}` | String | 5 min | 幂等去重，防客户端重发 |
| `im:retry:{nodeId}` | ZSet | 永久（消费后删除） | score=sendTimestamp，ACK 重试队列 |
| `im:recent:{userId}` | ZSet | 永久 | score=lastMsgTimestamp，最近会话 |

## Kafka Topics

| Topic | 分区策略 | Consumer Group | 说明 |
|-------|---------|----------------|------|
| `im-message-persist` | 按 `conversationId` hash | `im-persist-group` | 消息持久化到 DB，会话内有序 |
| `im-message-push` | 按 `targetNodeId` hash | `im-push-group` | 跨节点推送（Pub/Sub 备选方案） |

## 消息可靠投递四层保障

| 层 | 机制 | 说明 |
|----|------|------|
| L1 | 客户端 UUID msgId | 重发时携带相同 ID |
| L2 | Redis `im:dedup:{msgId}` 5min | 服务端幂等去重 |
| L3 | Redis ZSet 重试队列 | 3s 未收到客户端 ACK 则重试，最多 3 次 |
| L4 | Kafka + DB 持久化 | 离线消息上线后通过 `/messages/sync` 拉取 |

## 跨节点路由逻辑

```
查 im:online:{targetUserId} 获取节点 ID
→ 同节点：直接 wsSessionManager.push()
→ 跨节点：Redis Pub/Sub channel im:node:{targetNode}
→ 离线：消息留存 Kafka，等用户上线后拉取
```

## 会话 ID 生成规则

- 单聊：`CONV_P_{min(uid1,uid2)}_{max(uid1,uid2)}`（双方生成相同 ID）
- 群聊：`CONV_G_{雪花ID}`

## 关键目录

| 类/目录 | 职责 |
|--------|------|
| `websocket/WsServer.java` | `@ServerEndpoint` 入口，处理连接/断开/消息 |
| `websocket/WsMessageDispatcher.java` | 按 `cmd` 分发到各 Handler |
| `websocket/WsSessionManager.java` | `userId → Session` 映射管理 |
| `retry/AckRetryTask.java` | 定时扫描 `im:retry:{nodeId}` ZSet，触发重推 |
| `mq/MessagePersistConsumer.java` | Kafka Consumer，批量写入 `im_message` 表 |

## 接口（详见 [docs/API.md §6-7](../../docs/API.md)）

- `GET /im/api/v1/messages/sync` — 离线消息拉取
- `GET /im/api/v1/conversations` — 会话列表
- WebSocket：`ws://gateway:9000/im/ws?token=<JWT>`

## 错误码段

`5021-5030`，定义在 `campus-common` 的 `ErrorCode` 枚举。
