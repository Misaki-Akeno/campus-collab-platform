package com.campus.seckill.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.Result;
import com.campus.seckill.entity.SeckillActivity;
import com.campus.seckill.entity.SeckillOrder;
import com.campus.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    /** GET /api/v1/activities — 活动列表（公开） */
    @GetMapping("/activities")
    public Result<Page<SeckillActivity>> listActivities(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(seckillService.listActivities(page, size));
    }

    /** GET /api/v1/activities/{activityId} — 活动详情（公开） */
    @GetMapping("/activities/{activityId}")
    public Result<SeckillActivity> getActivity(@PathVariable Long activityId) {
        return Result.ok(seckillService.getActivity(activityId));
    }

    /** POST /api/v1/activities/{activityId}/book — 秒杀报名（需登录） */
    @PostMapping("/activities/{activityId}/book")
    public Result<Map<String, Object>> book(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long activityId) {
        String orderId = seckillService.book(activityId, userId);
        return Result.ok("报名排队中，请轮询 /api/v1/orders/" + orderId + " 查看结果", Map.of("orderId", orderId));
    }

    /** GET /api/v1/orders/{orderId} — 查询订单结果（需登录，校验归属） */
    @GetMapping("/orders/{orderId}")
    public Result<SeckillOrder> getOrder(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long orderId) {
        SeckillOrder order = seckillService.getOrder(orderId);
        if (!order.getUserId().equals(userId)) {
            throw new com.campus.common.exception.BizException(
                    com.campus.common.exception.ErrorCode.FORBIDDEN);
        }
        return Result.ok(order);
    }
}
