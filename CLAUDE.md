# 校园社团协作平台 — 开发指南

## 项目概览

高校社团数字化协作平台，微服务架构，覆盖社团管理、IM 即时通讯、活动秒杀、大文件上传、AI Agent。

## 核心文档（开始前必读）

执行项目修复与规划时先从 [tasks.md](tasks.md) 的当前阶段开始：建立差异清单，修复代码与文档不一致并完成运行验收，然后再制定后续分步与分布式方案。优化依据见 [docs/优化与演进.md](docs/优化与演进.md)，任务完成以执行记录为准。

| 文档 | 内容 |
|------|------|
| [tasks.md](tasks.md) | Agent 执行入口：第一阶段修复与验收，第二阶段制定后续方案 |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 系统架构决策：服务拓扑、技术选型、数据库ER图、安全体系、部署架构 |
| [docs/API.md](./docs/API.md) | 接口契约：全部 REST API（Request/Response）+ WebSocket 协议 |
| [docs/优化与演进.md](docs/优化与演进.md) | 后续优化候选、前置条件、测量方式与分布式部署依据 |
| [Improvement Plan 模板](docs/improvement-plans/TEMPLATE.md) | 演进方案统一格式；按 Improvement Plan N：主题命名，并登记优化文档中的方案索引 |

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
| `campus-platform-backend/campus-seckill-service` | 活动报名：MySQL 条件扣库存/同步订单终态 | 8085 |
| `campus-platform-backend/campus-common` | 公共基础模块（被所有服务依赖） | — |
| `campus-platform-backend/campus-api` | Feign 服务契约（跨服务通信接口） | — |
| `campus-platform-frontend` | Next.js Web 前端 | 3000 |
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

## 测试

**框架**: JUnit 5 + Mockito + Spring Test
**当前基线（2026-09-05）**：JDK 21.0.6 / Maven 3.9.16 执行 `mvn -B -ntp test`，152 用例通过，0 failures/errors/skipped。Docker 集成测试未包含在此数字中，准确状态见 `tasks.md`。

```
campus-common/          # 3 用例
campus-api/             # 4 用例
campus-user-service/    # 28 用例
campus-club-service/    # 35 用例
campus-im-service/      # 34 用例
campus-file-service/    # 23 用例
campus-seckill-service/ # 25 用例
```

| 层级 | 技术 | 注意事项 |
|------|------|---------|
| **Service** | @ExtendWith(MockitoExtension) + @InjectMocks | MyBatis-Plus `insert()` / `updateById()` 有单参/Collection 参数歧义，**不要** `verify(mapper).insert(any())`，改用 `@MockitoSettings(Strictness.LENIENT)` |
| **Controller** | Standalone MockMvc + GlobalExceptionHandler | 不用 @WebMvcTest（与 MyBatis-Plus 有版本冲突），使用 `MockMvcBuilders.standaloneSetup().setControllerAdvice()` |

**HTTP 端到端测试** (Bruno):
> 测试集定义在 `tests/bruno/`，通过 `make http-test` 或 `make http-test-ci` 执行。
> 覆盖全服务 API 链路：注册 → 登录 → Token 刷新 → 社团/活动/IM/文件操作。

## Git 分支策略

| 分支 | 用途 |
|------|------|
| `main` | 生产分支，仅 MR 合入，需 1 人 Review |
| `develop` | 开发集成分支 |
| `feature/{module}-{desc}` | 功能分支，如 `feature/seckill-lua-stock` |
| `hotfix/{desc}` | 紧急修复，从 main 拉出 |

## GitHub Actions / CI

**远程仓库**: https://github.com/Misaki-Akeno/campus-collab-platform
**本地 gh CLI**: 已安装（v2.91.0），已完成 auth login

| 命令 | 说明 |
|------|------|
| `gh run list --repo Misaki-Akeno/campus-collab-platform` | 查看最近 Actions 记录 |
| `gh run view <run-id> --repo Misaki-Akeno/campus-collab-platform` | 查看 run 详情 |
| `gh run view <run-id> --repo Misaki-Akeno/campus-collab-platform --log-failed` | 查看失败日志 |

**workflow 文件**: `.github/workflows/backend-quality-gate.yml`

已知问题：`Wait for services` 步骤（等待 6 个 Java 服务的 `/actuator/health`）超时（exit 124），
根因见下方 "CI 已知问题" 节。

## CI 已知问题

**问题**: `Wait for services` 步骤 `timeout 120` 超时（exit 124），6 个服务（端口 9000/8081–8085）均未在 120s 内就绪。

**可能根因**（按优先级）：

1. **Kafka 健康检查占用时间过长** — `start_period: 30s` + `retries: 10` × `interval: 15s` = 最长 180s 才 healthy，而 workflow 中 `Wait for middleware` 每个容器超时仅 120s，Kafka 可能尚未 healthy 就进入下一步，导致依赖 Kafka 的 seckill/im 服务启动失败。

2. **workflow 未调用 `make wait-healthy`** — Makefile 里有完善的 `wait-healthy` target，但 workflow 的 `Wait for middleware` 步骤是手写的 `docker inspect` 轮询，各自 120s 串行执行，总等待时间可能不足。

3. **Java 服务 6 个串行冷启动 + Spring Boot 初始化** — CI runner 资源有限，120s 对 6 个服务同时冷启动可能不够。

4. **`run-all` 用 `nohup java -jar` 启动后 `stop-all` 会删除 `logs/` 目录** — 失败时 artifact 上传找不到日志（CI 日志显示 `No files were found with the provided path: logs/`）。

## 变更记录

修改任何功能后在 [CHANGELOG.md](./CHANGELOG.md) 中追加记录。
