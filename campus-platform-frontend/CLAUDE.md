# campus-platform-frontend 开发指南

## 技术栈

React Native **0.79+**，New Architecture（Fabric + TurboModules）默认启用，渲染性能提升约 30%。

## 关键目录

| 路径 | 说明 |
|------|------|
| `src/services/api.ts` | Axios 实例，Base URL: `gateway:9000`，自动注入 `Bearer Token`，401 自动刷新 |
| `src/services/wsClient.ts` | WebSocket 客户端，连接 `ws://gateway:9000/im/ws?token=<JWT>` |
| `src/utils/md5Worker.ts` | Web Worker 计算整文件 MD5（文件上传的前置步骤） |
| `src/utils/chunkUploader.ts` | 分片上传器：并发 3 片，支持断点续传 |
| `src/store/` | 状态管理（Zustand / Redux） |

## Token 管理规则

- `accessToken` 存 SecureStorage，有效期 2h，**过期时自动用 `refreshToken` 静默刷新**
- `refreshToken` 存 SecureStorage，有效期 7d
- 401 响应触发自动刷新流程；刷新失败则清除 Token，跳转登录页

## API 请求规范

- 所有接口统一通过 Gateway（端口 9000）访问，禁止直连各业务服务
- 响应格式：`{ code, msg, data, traceId }`，`code === 200` 为成功
- 详细接口参考：[docs/API.md](../docs/API.md)

## 文件上传流程

1. 用 `md5Worker.ts` 计算整文件 MD5（Web Worker 避免阻塞 UI）
2. `POST /file/api/v1/upload/init`（判断秒传）
3. 并发 3 片 `PUT` 到 OSS 预签名 URL
4. 每片完成后 `POST /file/api/v1/upload/chunk/complete`
5. 全部完成后 `POST /file/api/v1/upload/merge`

## WebSocket 使用

- 连接后监听 `PUSH_MSG` 指令处理新消息推送
- 每 30s 发送 `HEARTBEAT` 保活
- 发送消息用本地 UUID 作 `msgId`，等待 `ACK` 后更新本地消息状态

## 架构参考

- 接口文档：[docs/API.md](../docs/API.md)
- 整体架构：[ARCHITECTURE.md](../ARCHITECTURE.md)
