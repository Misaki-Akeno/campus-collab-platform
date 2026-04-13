-- seckill_deduct.lua
-- 秒杀原子扣减脚本（防重 + 库存扣减，Redis 端一次 round-trip 完成）
--
-- KEYS[1] = sk:stock:{activityId}   库存 key（String，预热时 SET）
-- KEYS[2] = sk:booked:{activityId}  已报名用户 Set
-- ARGV[1] = userId
--
-- 返回值:
--   >= 0  扣减后剩余库存（成功）
--   -1    库存不足
--   -2    重复报名
--   -3    活动库存 key 不存在（未预热）

local stockKey  = KEYS[1]
local bookedKey = KEYS[2]
local userId    = ARGV[1]

-- 检查活动是否已预热
local stock = redis.call('GET', stockKey)
if stock == false then
    return -3
end

-- 检查重复报名
if redis.call('SISMEMBER', bookedKey, userId) == 1 then
    return -2
end

-- 库存不足
if tonumber(stock) <= 0 then
    return -1
end

-- 原子扣减库存并记录用户
redis.call('DECR', stockKey)
redis.call('SADD', bookedKey, userId)
return tonumber(stock) - 1
