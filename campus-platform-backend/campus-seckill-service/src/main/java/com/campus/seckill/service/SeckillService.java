package com.campus.seckill.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.seckill.entity.SeckillActivity;
import com.campus.seckill.entity.SeckillOrder;

public interface SeckillService {

    /** 活动列表（公开，分页；clubId/status 为可选过滤条件） */
    Page<SeckillActivity> listActivities(int pageNum, int pageSize, Long clubId, Integer status);

    /** 活动详情 */
    SeckillActivity getActivity(Long activityId);

    /**
     * 秒杀报名（核心高并发接口）。
     * <p>
     * 先执行 Redis Lua 原子扣减，成功后直接写入 SUCCESS 状态订单；
     * Lua 失败（库存不足/重复报名/未预热）直接抛异常，无数据库操作。
     * Kafka 消息仅用于异步对账/补偿。
     * </p>
     * @return orderId
     */
    String book(Long activityId, Long userId);

    /** 查询订单结果 */
    SeckillOrder getOrder(Long orderId);
}
