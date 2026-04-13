package com.campus.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.constant.KafkaTopicConstant;
import com.campus.common.constant.RedisKeyConstant;
import com.campus.common.exception.BizException;
import com.campus.common.exception.ErrorCode;
import com.campus.seckill.entity.SeckillActivity;
import com.campus.seckill.entity.SeckillOrder;
import com.campus.seckill.mapper.SeckillActivityMapper;
import com.campus.seckill.mapper.SeckillOrderMapper;
import com.campus.seckill.service.SeckillService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private final SeckillActivityMapper activityMapper;
    private final SeckillOrderMapper orderMapper;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /** Lua 脚本：原子扣减库存 + 防重（返回剩余库存 / 负数错误码） */
    private DefaultRedisScript<Long> deductScript;

    @PostConstruct
    public void initScript() {
        deductScript = new DefaultRedisScript<>();
        deductScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/seckill_deduct.lua")));
        deductScript.setResultType(Long.class);
    }

    @Override
    public Page<SeckillActivity> listActivities(int pageNum, int pageSize) {
        return activityMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SeckillActivity>()
                        .orderByDesc(SeckillActivity::getStartTime));
    }

    @Override
    public SeckillActivity getActivity(Long activityId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "活动不存在");
        }
        return activity;
    }

    /**
     * 秒杀报名核心流程（高并发）：
     * <ol>
     *   <li>时间窗口前置校验（DB 读，可加缓存优化）</li>
     *   <li>Redis Lua 原子扣减（防重 + 库存，单 round-trip）</li>
     *   <li>Lua 成功后发 Kafka，Consumer 异步落库</li>
     * </ol>
     * DB 订单由 Kafka Consumer 创建，避免在 Lua 失败前产生幽灵订单。
     *
     * @return 客户端轮询用的临时 token（格式：activityId:userId，Consumer 落库后可换取真实 orderId）
     */
    @Override
    public String book(Long activityId, Long userId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "活动不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            throw new BizException(ErrorCode.ACTIVITY_NOT_START);
        }
        if (now.isAfter(activity.getEndTime())) {
            throw new BizException(ErrorCode.ACTIVITY_ENDED);
        }

        String stockKey  = String.format(RedisKeyConstant.SECKILL_STOCK,  activityId);
        String bookedKey = String.format(RedisKeyConstant.SECKILL_BOOKED, activityId);

        // Lua 原子扣减：防重 + 库存检查 + DECR + SADD 四步一次完成
        Long result = redisTemplate.execute(
                deductScript,
                List.of(stockKey, bookedKey),
                String.valueOf(userId));

        if (result == null || result == -3L) {
            // 活动库存未预热，降级为 DB 查询
            log.warn("秒杀库存未预热: activityId={}", activityId);
            throw new BizException(ErrorCode.STOCK_EMPTY);
        }
        if (result == -2L) {
            throw new BizException(ErrorCode.DUPLICATE_BOOK);
        }
        if (result == -1L) {
            throw new BizException(ErrorCode.STOCK_EMPTY);
        }

        // Lua 扣减成功，发 Kafka 异步落库
        String payload = String.format(
                "{\"userId\":%d,\"activityId\":%d,\"remainStock\":%d}",
                userId, activityId, result);
        kafkaTemplate.send(KafkaTopicConstant.TOPIC_SECKILL_ORDER,
                String.valueOf(activityId), payload);

        log.info("秒杀报名成功入队: userId={}, activityId={}, remainStock={}", userId, activityId, result);
        // 返回客户端轮询 token，真实 orderId 由 Kafka Consumer 落库后写入 Redis 供查询
        return activityId + ":" + userId;
    }

    @Override
    public SeckillOrder getOrder(Long orderId) {
        SeckillOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }
}
