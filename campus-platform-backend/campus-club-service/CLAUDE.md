# campus-club-service 开发指南

## 职责

社团域（端口 **8082**）：社团 CRUD 及审核、成员管理（申请/审批/踢人）、活动 CRUD、公告发布。

## 技术栈

Spring WebMVC + JDK 21 虚拟线程（`spring.threads.virtual.enabled=true`）
MySQL + Redis（缓存热点数据）+ MyBatis-Plus

## 数据库表（完整 DDL 见白皮书 §3.2-A）

| 表名 | 说明 | 关键索引 |
|------|------|---------|
| `club` | 社团主表 | `uk_name`（唯一） |
| `club_member` | 成员关联表 | `uk_user_club`（联合唯一，防重复加入） |
| `club_announcement` | 公告表 | `idx_club_pinned`（club_id+is_pinned+create_time） |

## 权限规则

**全局角色**（从 Gateway Header `X-User-Role` 获取）：
- 社团创建申请：任何已登录用户
- 社团审核（approve/reject）：需全局 `role = 2`（管理员）

**社团内角色**（`club_member.member_role`）：
- `0`：普通成员
- `1`：副社长
- `2`：社长

发布/编辑公告需 `member_role >= 1`。

## 社团状态机

`club.status`：`0`-待审核 → `1`-正常 / `2`-已解散

## 用户信息获取

- 当前用户信息：从 Gateway 注入的 `X-User-Id`、`X-User-Role` Header 读取
- 跨服务查用户详情：通过 `campus-api` 中的 `UserFeignClient`

## 接口（详见 [docs/API.md §3](../../docs/API.md)）

| 方法 | 路径 | 权限 |
|------|------|------|
| POST | `/club/api/v1/clubs` | 已登录 |
| GET | `/club/api/v1/clubs` | 公开 |
| POST | `/club/api/v1/clubs/{clubId}/join` | 已登录 |
| POST | `/club/api/v1/clubs/{clubId}/announcements` | member_role >= 1 |

## 错误码段

`5031-5040`，定义在 `campus-common` 的 `ErrorCode` 枚举。
