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
     * 内部通过 Redis Lua 原子扣减，成功后发 Kafka 异步落库。
     * DB 订单由 Consumer 创建，此方法不直接写 DB。
     * </p>
     * @return 轮询 token（格式: activityId:userId），Consumer 落库后可通过 /orders 接口查询真实 orderId
     */
    String book(Long activityId, Long userId);

    /** 查询订单结果 */
    SeckillOrder getOrder(Long orderId);
}
