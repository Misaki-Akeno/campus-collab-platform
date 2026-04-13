package com.campus.seckill.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.seckill.entity.SeckillActivity;
import com.campus.seckill.entity.SeckillOrder;

public interface SeckillService {

    /** 活动列表（公开，分页） */
    Page<SeckillActivity> listActivities(int pageNum, int pageSize);

    /** 活动详情 */
    SeckillActivity getActivity(Long activityId);

    /**
     * 秒杀报名（核心高并发接口）。
     * <p>
     * 内部预创建 PROCESSING 状态订单 → Redis Lua 原子扣减 →
     * Kafka 异步更新订单为 SUCCESS。Lua 失败则回滚删除订单。
     * </p>
     * @return orderId，客户端可轮询 /api/v1/orders/{orderId}
     */
    String book(Long activityId, Long userId);

    /** 查询订单结果 */
    SeckillOrder getOrder(Long orderId);
}
