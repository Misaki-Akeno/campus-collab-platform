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
import org.springframework.transaction.annotation.Transactional;

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
     *   <li>活动状态 + 时间窗口前置校验</li>
     *   <li>预创建状态 = PROCESSING(0) 的订单，获取 orderId</li>
     *   <li>Redis Lua 原子扣减（防重 + 库存，单 round-trip）</li>
     *   <li>Lua 成功后发 Kafka，Consumer 异步更新订单为 SUCCESS</li>
     *   <li>Lua 失败回滚：删除预创建订单，抛业务异常</li>
     * </ol>
     * 客户端拿到 orderId 后可通过 /orders/{orderId} 轮询最终状态。
     *
     * @return 订单 orderId，客户端可轮询 /api/v1/orders/{orderId}
     */
    @Override
    @Transactional
    public String book(Long activityId, Long userId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "活动不存在");
        }

        // 活动状态校验
        if (activity.getStatus() != 1) {
            throw new BizException(ErrorCode.ACTIVITY_CANCELLED);
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            throw new BizException(ErrorCode.ACTIVITY_NOT_START);
        }
        if (now.isAfter(activity.getEndTime())) {
            throw new BizException(ErrorCode.ACTIVITY_ENDED);
        }

        // 预创建订单（status=0 排队中），客户端拿到 orderId 即可轮询
        SeckillOrder order = new SeckillOrder();
        order.setUserId(userId);
        order.setActivityId(activityId);
        order.setStatus(0); // PROCESSING
        orderMapper.insert(order);
        Long orderId = order.getId();

        String stockKey  = String.format(RedisKeyConstant.SECKILL_STOCK,  activityId);
        String bookedKey = String.format(RedisKeyConstant.SECKILL_BOOKED, activityId);

        // Lua 原子扣减：防重 + 库存检查 + DECR + SADD 四步一次完成
        Long result = redisTemplate.execute(
                deductScript,
                List.of(stockKey, bookedKey),
                String.valueOf(userId));

        if (result == null || result == -3L) {
            // 活动库存未预热，回滚订单
            log.warn("秒杀库存未预热: activityId={}, orderId={}, 已回滚订单", activityId, orderId);
            orderMapper.deleteById(orderId);
            throw new BizException(ErrorCode.STOCK_EMPTY);
        }
        if (result == -2L) {
            orderMapper.deleteById(orderId);
            throw new BizException(ErrorCode.DUPLICATE_BOOK);
        }
        if (result == -1L) {
            orderMapper.deleteById(orderId);
            throw new BizException(ErrorCode.STOCK_EMPTY);
        }

        // Lua 扣减成功，发 Kafka 异步更新订单为 SUCCESS
        String payload = String.format(
                "{\"orderId\":%d,\"userId\":%d,\"activityId\":%d,\"remainStock\":%d}",
                orderId, userId, activityId, result);
        kafkaTemplate.send(KafkaTopicConstant.TOPIC_SECKILL_ORDER,
                String.valueOf(activityId), payload);

        log.info("秒杀报名成功入队: orderId={}, userId={}, activityId={}, remainStock={}", orderId, userId, activityId, result);
        return String.valueOf(orderId);
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
