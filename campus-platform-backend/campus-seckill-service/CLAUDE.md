# campus-seckill-service 开发指南

## 职责

活动报名（端口 **8085**）：活动查询、库存条件扣减、报名订单与归属查询。

## 技术栈

Spring WebMVC + MySQL + MyBatis-Plus。

## 一致性模型

```
校验活动状态和时间窗口
  → 查询用户是否已有订单
  → UPDATE ... SET available_stock = available_stock - 1 WHERE available_stock > 0
  → 同一事务写入 SUCCESS 订单
```

`available_stock > 0` 条件更新保证库存不为负；`uk_user_activity` 唯一键裁决并发重复请求。
库存扣减与订单插入在一个 MySQL 事务中，接口成功返回时订单已经持久化为 `SUCCESS`。

## 接口（详见 [docs/API.md §4](../../docs/API.md)）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/seckill/api/v1/activities/{id}/book` | 核心高频接口 |
| GET | `/seckill/api/v1/orders/{orderId}` | 查询已提交订单 |
| GET | `/seckill/api/v1/activities` | 活动列表（公开） |

## 错误码段

`5001-5010`，定义在 `campus-common` 的 `ErrorCode` 枚举。
