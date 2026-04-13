# campus-seckill-service 开发指南

## 职责

高并发秒杀报名（端口 **8085**）：活动库存管理、Redis Lua 原子扣减、Kafka 异步下单、防刷限流、库存对账。

## 技术栈

Spring WebMVC + JDK 21 虚拟线程（`spring.threads.virtual.enabled=true`）
Redis + Kafka + MySQL + MyBatis-Plus

## 三级流量漏斗

```
瞬时流量 10000 QPS
  → L1: Gateway Sentinel 令牌桶（1000 QPS）
  → L2: 用户维度防刷（滑动窗口 5s/次）
  → L3: Redis Lua 原子扣减（微秒级判定）
  → Kafka 异步下单
  → MySQL Consumer 落盘
```

## 核心 Redis Key

| Key 模板 | 结构 | TTL | 说明 |
|---------|------|-----|------|
| `sk:stock:{activityId}` | String | 活动结束后清理 | 实时可用库存 |
| `sk:users:{activityId}` | Set | 活动结束后清理 | 已报名用户 ID（防重复） |
| `sk:detail:{activityId}` | String(JSON) | 10 min | 活动详情缓存 |

## Lua 脚本逻辑

```
KEYS[1]=sk:stock:{id}, KEYS[2]=sk:users:{id}, ARGV[1]=userId
返回: >=0 剩余库存（成功）| -1 库存不足 | -2 重复报名 | -3 活动不存在
```

> 完整脚本 `seckill_deduct.lua` 见白皮书 §5.3.2

## Kafka Topic

`seckill-order`：按 `activityId` hash 分区，Consumer Group: `seckill-order-group`
Consumer 写入 `seckill_order` 表，更新 `status` → `SUCCESS` / `FAILED`。

## 订单状态机

```
[提交报名] → PROCESSING → SUCCESS（Kafka 消费成功）
                       → FAILED（库存不足/异常）
             SUCCESS → CANCELLED（用户取消）
```

## 库存预热与对账

- **预热**：活动创建时 `SET sk:stock:{id} totalStock` + `DEL sk:users:{id}`
- **对账**：定时任务每分钟对比 Redis 库存 vs DB 订单数，不一致则告警 + 修正
- 类：`StockWarmUpService`（预热）、`StockReconcileTask`（对账定时任务）

## 接口（详见 [docs/API.md §4](../../docs/API.md)）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/seckill/api/v1/activities/{id}/book` | 核心高频接口 |
| GET | `/seckill/api/v1/orders/{orderId}` | 查询订单结果 |
| GET | `/seckill/api/v1/activities` | 活动列表（公开） |

## 压测验收标准

5000 并发，库存 500：最终成功订单 = 500（不超卖），P99 < 500ms，无 5xx。

## 错误码段

`5001-5010`，定义在 `campus-common` 的 `ErrorCode` 枚举。
