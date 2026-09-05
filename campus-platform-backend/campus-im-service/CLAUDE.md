# campus-im-service 开发指南

## 职责

IM 消息系统（端口 **8083**）：WebSocket 长连接管理、消息路由分发、ACK 重试机制、离线消息同步、已读回执。**系统中唯一的有状态服务**（持有 WS 连接）。

## 技术栈

Spring WebMVC + WebSocket + Redis Pub/Sub + MySQL + MyBatis-Plus。

## 核心 Redis Key

| Key 模板 | 结构 | TTL | 说明 |
|---------|------|-----|------|
| `im:online:{userId}` | String | WS 连接同生命周期 | value = `nodeId|sessionId`，用于条件删除和跨节点踢旧连接 |

## 消息接收与幂等

1. 校验发送者是会话成员。
2. 按 `(sender_id, client_msg_id)` 查询数据库幂等记录。
3. 新消息同步写入 `im_message`；写入成功后才发送成功 ACK 和实时推送。
4. 重复请求返回首次写入的真实 `serverMsgId`；写入异常不返回成功 ACK。

`im-message-persist` Consumer 仅用于兼容历史生产者，不参与当前 WebSocket 成功路径；非重复持久化异常会抛出并触发 Kafka 重试。

## 跨节点路由逻辑

```
查 im:online:{targetUserId} 获取节点和 session 所有权
→ 同节点：直接 wsSessionManager.push()
→ 跨节点：Redis Pub/Sub channel im:node:{targetNode}
→ 离线：消息已在数据库持久化，用户上线后通过同步接口拉取
```

## 会话 ID 生成规则

- 单聊：`CONV_P_{min(uid1,uid2)}_{max(uid1,uid2)}`（双方生成相同 ID）
- 群聊：`CONV_G_{雪花ID}`

## 关键目录

| 类/目录 | 职责 |
|--------|------|
| `websocket/WsServer.java` | WebSocket 入口，处理连接、路由所有权、断开和消息 |
| `websocket/WsMessageDispatcher.java` | 按 `cmd` 分发到各 Handler |
| `websocket/WsSessionManager.java` | `userId → Session` 映射管理 |
| `websocket/handler/ChatMsgHandler.java` | 权限校验、同步落库、幂等 ACK 和实时推送 |
| `mq/MessagePersistConsumer.java` | 兼容历史 Kafka 消息，异常向监听容器抛出 |

## 接口（详见 [docs/API.md §6-7](../../docs/API.md)）

- `GET /im/api/v1/messages/sync` — 离线消息拉取
- `GET /im/api/v1/conversations` — 会话列表
- WebSocket：`ws://gateway:9000/im/ws?token=<JWT>`

## 错误码段

`5021-5030`，定义在 `campus-common` 的 `ErrorCode` 枚举。
