# 校园社团协作平台

> 面向高校场景的一站式社团数字化协作平台，覆盖社团组织管理、实时即时通讯、热门活动抢报到大文件协作共享的全链路需求。

## 技术栈

- **后端**: Spring Boot 3.3.13 + Spring Cloud 2023.0.3 + Spring Cloud Alibaba 2023.0.3.4 (JDK 21)
- **网关**: Spring Cloud Gateway + Nacos + Sentinel
- **数据存储**: MySQL 8.4 + Redis 7.4.8 + Kafka 4.0.1
- **文件存储**: MinIO / 阿里云 OSS
- **Web 前端**: Next.js 16.2.5 + React 19.2.6
- **AI 服务**: Python + FastAPI 

> 2026-09-05 已修订设计文档，目标能力尚待分项实现。当前状态与优化优先级见 [tasks.md](tasks.md)，接口现状与 v2 目标见 [docs/API.md](docs/API.md)。

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

使用轻量级 CLI 工具 `bru` 对运行中的服务执行端到端 API 验证。测试用例定义在 `tests/bruno/`，包含注册、登录、Token 刷新及社团/活动/IM/文件接口用例，完整用户与恢复流程按 tasks.md 补齐。

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
| **HTTP 端到端** | Bruno CLI + `.bru` 测试集 | 跨服务 API 验证（真实上传与故障场景待补） |

**测试状态**：下列为仓库已有测试结构，最近执行结果以 CI/本地报告为准；恢复、权限和真实文件上传场景按 [tasks.md](tasks.md) 补齐。

## CI/CD

GitHub Actions 工作流定义在 `.github/workflows/backend-quality-gate.yml`，触发条件为 `push/PR` 到 `main` 或 `develop`。

```
push/PR → [set up JDK 21 + Node.js + Bruno CLI]
        → [docker compose up (中间件)]
        → [等待 MySQL/Redis/Nacos/MinIO 健康]
        → [mvn package -DskipTests]
        → [java -jar 后台启动 6 服务]
        → [等待端口 9000/8081-8085 健康]
        → [mvn test (单元测试)]
        → [bru run (HTTP 端到端测试)]
        → [失败时上传 logs/ artifact]
        → [清理所有容器和进程]
```

| 阶段 | 超时 | 说明 |
|------|------|------|
| 环境准备 | 60s | JDK 21 / Maven 缓存 / Node.js 20 / Bruno CLI |
| 启动中间件 | 300s | MySQL/Redis/Kafka/Nacos/MinIO |
| 编译+启动服务 | 600s | mvn clean package + 后台启动 6 服务 |
| 单元测试 | 300s | JUnit 5（数量以执行报告为准） |
| HTTP 测试 | 60s | Bruno 端到端验证 |

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
├── campus-platform-frontend/   # Next.js Web 前端
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

- [Agent 任务入口](tasks.md)：先修复代码与文档一致性并验收，再制定后续方案。
- [技术架构与项目白皮书](./docs/校园社团协作平台-技术架构与项目白皮书.md)
- [优化与演进](docs/优化与演进.md)：优化候选、验证方法与分布式部署依据。
- [Improvement Plan 模板](docs/improvement-plans/TEMPLATE.md) · [Improvement Plan 1：业务闭环与分布式可靠性演进](docs/improvement-plans/improvement-plan-1.md)

## License

MIT
