# campus-gateway 开发指南

## 职责

统一入口服务（端口 **9000**）：JWT 鉴权、Sentinel 限流、路由转发、跨域处理。
使用 **Spring Cloud Gateway（WebFlux/Reactor 响应式模型）**。

## 重要约束

- **禁止开启** `spring.threads.virtual.enabled=true`，Gateway 保持 Reactive 模型，避免破坏 Reactor 事件循环
- 依赖 Nacos（服务发现 + 配置中心），**不依赖 MySQL**

## 路由规则

| 路由前缀 | 目标服务 | 端口 |
|--------|--------|------|
| `/user/**` | user-service | 8081 |
| `/club/**` | club-service | 8082 |
| `/im/**` | im-service | 8083 |
| `/file/**` | file-service | 8084 |
| `/seckill/**` | seckill-service | 8085 |

> 路由配置由 Nacos 配置中心管理，本地开发见 `src/main/resources/bootstrap.yml`

## 鉴权白名单（无需 Token）

```
POST /user/api/v1/register
POST /user/api/v1/login
POST /user/api/v1/token/refresh
GET  /seckill/api/v1/activities
GET  /club/api/v1/clubs
```

## JWT 校验逻辑（JwtAuthFilter）

1. 从 `Authorization: Bearer <token>` 提取 token
2. 验证签名（HMAC-SHA256）+ 过期时间
3. 查 Redis `user:blacklist:{jti}` 是否存在（注销/强制下线场景）
4. 合法则注入 `X-User-Id` / `X-User-Role` Header 给下游；否则返回 401

## 核心组件

| 类 | 职责 |
|----|------|
| `JwtAuthFilter` | 全局鉴权过滤器（GlobalFilter） |
| `RateLimitFilter` | Sentinel 令牌桶限流 |
| `TraceIdFilter` | 注入 SkyWalking traceId |
| `CorsConfig` | 跨域配置 |
| `SentinelConfig` | 限流规则（秒杀接口 QPS ≤ 1000） |

## 开发参考

- 安全体系整体设计：[ARCHITECTURE.md §5](../../ARCHITECTURE.md)
- 接口白名单完整列表：[docs/API.md §1.3](../../docs/API.md)
