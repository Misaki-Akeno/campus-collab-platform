# campus-user-service 开发指南

## 职责

账号管理（端口 **8081**）：用户注册、登录、JWT 签发/刷新/注销、用户信息 CRUD、RBAC 角色。

## 技术栈

Spring WebMVC + JDK 21 虚拟线程（`spring.threads.virtual.enabled=true`）
MySQL + Redis + MyBatis-Plus

## 数据库

主表：`sys_user`（完整 DDL 见白皮书 §3.2-A）

| 字段 | 说明 |
|------|------|
| `id` | 雪花算法 ID |
| `username` | 登录名，唯一索引 |
| `password` | BCrypt 加密（cost=12） |
| `role` | 全局角色：0-学生 / 1-社长 / 2-管理员 |
| `status` | 账号状态：0-禁用 / 1-正常 |

## 核心业务规则

- JWT `accessToken` 有效期 2h，存客户端；`refreshToken` 有效期 7d，存 Redis
- Redis Key：`user:refresh:{userId}`（TTL 7d）
- Token 注销/改密：将 `jti` 写入 `user:blacklist:{jti}`（TTL = Token 剩余有效期）
- `JwtUtil`、`SnowflakeIdUtil` 来自 `campus-common`，注入 Bean 使用

## JWT Payload 结构

```json
{ "sub": "userId", "username": "zhangsan", "role": 0, "jti": "uuid-xxxx", "iat": ..., "exp": ... }
```

## 接口（详见 [docs/API.md §2](../../docs/API.md)）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/user/api/v1/register` | 注册 |
| POST | `/user/api/v1/login` | 登录（返回双 Token） |
| POST | `/user/api/v1/token/refresh` | 刷新 accessToken |
| GET | `/user/api/v1/me` | 获取当前用户信息 |

## Feign 暴露

`UserFeignClient`（定义在 campus-api）供 club/im/seckill 服务查询用户基本信息（`UserBasicDTO`）。

## 开发参考

- 认证流程图：[ARCHITECTURE.md §5.1](../../ARCHITECTURE.md)
