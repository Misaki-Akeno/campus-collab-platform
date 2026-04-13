# 校园社团协作平台 — 开发指南

## 项目概览

高校社团数字化协作平台，微服务架构，覆盖社团管理、IM 即时通讯、活动秒杀、大文件上传、AI Agent。

## 核心文档（开始前必读）

| 文档 | 内容 |
|------|------|
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 系统架构决策：服务拓扑、技术选型、数据库ER图、安全体系、部署架构 |
| [docs/API.md](./docs/API.md) | 接口契约：全部 REST API（Request/Response）+ WebSocket 协议 |

## 快速启动

```bash
make dev    # 启动中间件（MySQL / Redis / Kafka / Nacos / MinIO）
make build  # 编译所有后端服务
make test   # 运行单元测试
make stop   # 停止本地环境
```

## 子项目入口

| 目录 | 说明 | 端口 |
|------|------|------|
| `campus-platform-backend/campus-gateway` | API 网关：鉴权/限流/路由 | 9000 |
| `campus-platform-backend/campus-user-service` | 用户服务：注册/登录/JWT | 8081 |
| `campus-platform-backend/campus-club-service` | 社团服务：社团/活动/公告 | 8082 |
| `campus-platform-backend/campus-im-service` | IM 服务：WebSocket/消息路由 | 8083 |
| `campus-platform-backend/campus-file-service` | 文件服务：分片上传/秒传 | 8084 |
| `campus-platform-backend/campus-seckill-service` | 秒杀服务：Redis Lua/异步下单 | 8085 |
| `campus-platform-backend/campus-common` | 公共基础模块（被所有服务依赖） | — |
| `campus-platform-backend/campus-api` | Feign 服务契约（跨服务通信接口） | — |
| `campus-platform-frontend` | React Native 移动端应用 | — |
| `ai-bot` | Python FastAPI AI Agent 服务 | — |

各子项目目录下有独立 `CLAUDE.md`，包含该服务开发的最小必要上下文。

## 编码规范速查

| 规范 | 要求 |
|------|------|
| 包命名 | `com.campus.{服务名}.{层名}`，如 `com.campus.user.service` |
| REST 路径 | 全小写复数名词，`/api/v1/resources/{id}` |
| 返回值 | 统一使用 `Result<T>` 包装（来自 campus-common），禁止直接返回裸对象 |
| 数据库字段 | snake_case |
| 常量 | `UPPER_SNAKE_CASE` |
| 服务层 | Interface + Impl 分离 |

## Git 分支策略

| 分支 | 用途 |
|------|------|
| `main` | 生产分支，仅 MR 合入，需 1 人 Review |
| `develop` | 开发集成分支 |
| `feature/{module}-{desc}` | 功能分支，如 `feature/seckill-lua-stock` |
| `hotfix/{desc}` | 紧急修复，从 main 拉出 |

## 变更记录

修改任何功能后在 [CHANGELOG.md](./CHANGELOG.md) 中追加记录。
