# 校园社团协作平台

> 面向高校场景的一站式社团数字化协作平台，覆盖社团组织管理、实时即时通讯、热门活动抢报到大文件协作共享的全链路需求。

## 技术栈

- **后端**: Spring Boot 3.5 + Spring Cloud 2024 + Spring Cloud Alibaba 2024 (JDK 21)
- **网关**: Spring Cloud Gateway + Nacos + Sentinel
- **数据存储**: MySQL 8.4 + Redis 8.6.2 + Kafka 4.0.2
- **文件存储**: MinIO / 阿里云 OSS
- **移动端**: React Native 0.79+
- **AI 服务**: Python + FastAPI 

## 快速开始

### 1. 启动本地开发环境

```bash
make dev
```

一键启动 MySQL、Redis、Kafka、Nacos、MinIO。

### 2. 编译所有后端服务

```bash
make build
```

### 3. 停止本地环境

```bash
make stop
```

## 项目结构

```
campus-collab-platform/
├── campus-platform-backend/    # Java 后端
│   ├── campus-common/          # 公共模块
│   ├── campus-api/             # Feign 契约
│   ├── campus-gateway/         # API 网关
│   ├── campus-user-service/    # 用户服务
│   ├── campus-club-service/    # 社团服务
│   ├── campus-im-service/      # IM 消息服务
│   ├── campus-seckill-service/ # 秒杀报名服务
│   └── campus-file-service/    # 文件服务
├── campus-platform-frontend/   # React Native 前端
├── ai-bot/                     # Python AI Agent
├── docker/                     # Docker Compose 编排
└── docs/                       # 项目文档
```

## 开发规范

- 包命名: `com.campus.{服务名}.{层名}`
- REST 路径: `/api/v1/...`
- 返回值统一使用 `Result<T>` 包装
- 分支策略: `main` / `develop` / `feature/{module}-{desc}`

## 文档

- [技术架构与项目白皮书](./docs/校园社团协作平台%20——%20技术架构与项目白皮书.md)

## License

MIT
