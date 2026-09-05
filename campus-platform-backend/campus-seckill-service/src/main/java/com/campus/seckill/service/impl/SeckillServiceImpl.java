package com.campus.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BizException;
import com.campus.common.exception.ErrorCode;
import com.campus.seckill.entity.SeckillActivity;
import com.campus.seckill.entity.SeckillOrder;
import com.campus.seckill.mapper.SeckillActivityMapper;
import com.campus.seckill.mapper.SeckillOrderMapper;
import com.campus.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private final SeckillActivityMapper activityMapper;
    private final SeckillOrderMapper orderMapper;

    @Override
    public Page<SeckillActivity> listActivities(int pageNum, int pageSize, Long clubId, Integer status) {
        LambdaQueryWrapper<SeckillActivity> wrapper = new LambdaQueryWrapper<SeckillActivity>()
                .eq(clubId != null, SeckillActivity::getClubId, clubId)
                .eq(status != null, SeckillActivity::getStatus, status)
                .orderByDesc(SeckillActivity::getStartTime);
        return activityMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
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
     * 报名核心流程：
     * <ol>
     *   <li>活动状态 + 时间窗口前置校验</li>
     *   <li>数据库唯一约束防止重复报名</li>
     *   <li>条件 UPDATE 原子扣减库存，并在同一事务创建成功订单</li>
     * </ol>
     *
     * @return 订单 orderId
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

        Long existingOrders = orderMapper.selectCount(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getActivityId, activityId)
                .eq(SeckillOrder::getUserId, userId));
        if (existingOrders != null && existingOrders > 0) {
            log.info("重复报名拦截: activityId={}, userId={}", activityId, userId);
            throw new BizException(ErrorCode.DUPLICATE_BOOK);
        }

        // 库存和订单都在 MySQL 事务内；任一步失败都会回滚，不再产生 Redis/DB 双写窗口。
        int deducted = activityMapper.deductAvailableStock(activityId);
        if (deducted != 1) {
            log.info("库存不足: activityId={}, userId={}", activityId, userId);
            throw new BizException(ErrorCode.STOCK_EMPTY);
        }

        SeckillOrder order = new SeckillOrder();
        order.setUserId(userId);
        order.setActivityId(activityId);
        order.setStatus(1);
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException duplicate) {
            // 并发重复请求可能同时通过前置查询；抛业务异常以回滚本次库存扣减。
            throw new BizException(ErrorCode.DUPLICATE_BOOK);
        }
        Long orderId = order.getId();

        log.info("报名成功: orderId={}, userId={}, activityId={}", orderId, userId, activityId);
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
