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

### 3. 运行测试

```bash
make test       # 单元测试（快，无需启动服务）
make test-all   # 全自动流水线：单测 + HTTP 集成测试（需 Docker）
```

### 4. 停止本地环境

```bash
make stop
```

## 常用 Make 命令

| 命令 | 说明 | 前置条件 |
|------|------|----------|
| `make help` | 显示所有可用命令 | — |
| `make dev` | 启动中间件（MySQL/Redis/Kafka/Nacos/MinIO） | Docker |
| `make stop` | 停止中间件容器 | Docker |
| `make build` | 编译所有后端服务（跳过测试） | JDK 21 + Maven |
| `make test` | 运行单元测试 | — |
| `make test-all` | **全自动流水线**：启动中间件 → 编译 → 启动服务 → 单测 → HTTP 测试 → 清理 | Docker + JDK 21 + Maven + Bruno CLI |
| `make run-all` | 后台启动所有 6 个 Java 服务 | 需先 `make build` + `make dev` |
| `make stop-all` | 停止所有 Java 服务 + 中间件 + 清理日志 | — |
| `make http-test` | 交互模式运行 Bruno HTTP 测试 | Bruno CLI (`npm i -g @usebruno/cli`) |
| `make http-test-ci` | CI 模式运行 Bruno HTTP 测试（JSON 输出） | Bruno CLI |
| `make clean` | 清理 Maven 构建产物 | — |

### HTTP 测试（Bruno）

使用轻量级 CLI 工具 `bru` 对运行中的服务执行端到端 API 验证。测试用例定义在 `tests/bruno/`，覆盖了注册 → 登录 → Token 刷新 → 社团/活动/IM/文件全链路。

```bash
npm install -g @usebruno/cli    # 全局安装（仅一次）
make http-test                  # 本地交互模式查看结果
```

**测试覆盖**:

| 服务 | 测试用例 | 说明 |
|------|----------|------|
| 用户服务 | 注册 → 登录 → 刷新 Token → 获取用户信息 | 环境变量自动传递 userId / accessToken |
| 社团服务 | 社团列表 → 创建社团 → 加入社团 | clubId 链路传递 |
| 秒杀服务 | 活动列表 → 秒杀报名 | 验证多业务状态码 (200/5001/5002) |
| IM 服务 | 会话列表 → 离线消息同步 | 需鉴权 |
| 文件服务 | 初始化分片上传 | 验证上传类型返回 (new/instant/resume) |

**测试体系**:

| 层级 | 技术 | 覆盖范围 |
|------|------|----------|
| **Service 层** | JUnit 5 + Mockito + @InjectMocks | 注册/登录/Token 刷新/改密、社团 CRUD/成员管理 |
| **Controller 层** | Standalone MockMvc + GlobalExceptionHandler | 请求/响应映射、参数校验、异常处理 |
| **API 层** | 纯单元测试 | Feign 降级工厂行为验证 |
| **HTTP 端到端** | Bruno CLI + `.bru` 测试集 | 全服务 API 链路验证 |

**当前状态**: 67 个单元测试用例 + 12 个 HTTP 集成测试用例。

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
