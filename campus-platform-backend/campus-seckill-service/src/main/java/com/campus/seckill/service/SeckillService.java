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
     * 在同一数据库事务内条件扣减库存并写入 SUCCESS 订单。
     * 数据库唯一键防止重复报名，任一步失败都会回滚库存。
     * </p>
     * @return orderId
     */
    String book(Long activityId, Long userId);

    /** 查询订单结果 */
    SeckillOrder getOrder(Long orderId);
}
