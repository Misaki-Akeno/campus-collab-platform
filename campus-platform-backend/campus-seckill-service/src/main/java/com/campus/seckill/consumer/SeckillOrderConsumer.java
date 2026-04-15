package com.campus.seckill.consumer;

import com.campus.common.constant.KafkaTopicConstant;
import com.campus.seckill.entity.SeckillOrder;
import com.campus.seckill.mapper.SeckillOrderMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单异步落终态消费者：
 * 仅处理 PROCESSING(0) 订单，更新为 SUCCESS(1)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final SeckillOrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopicConstant.TOPIC_SECKILL_ORDER, groupId = "campus-seckill-consumer")
    public void onMessage(String message) {
        try {
            JsonNode payload = objectMapper.readTree(message);
            JsonNode orderIdNode = payload.get("orderId");
            Long orderId = orderIdNode == null ? null : orderIdNode.asLong();
            if (orderId == null) {
                log.warn("秒杀订单消息缺少 orderId: {}", message);
                return;
            }

            SeckillOrder current = orderMapper.selectById(orderId);
            if (current == null) {
                log.warn("秒杀订单不存在，忽略: orderId={}", orderId);
                return;
            }
            if (current.getStatus() != null && current.getStatus() != 0) {
                log.info("秒杀订单已是终态，跳过重复消费: orderId={}, status={}", orderId, current.getStatus());
                return;
            }

            SeckillOrder success = new SeckillOrder();
            success.setId(orderId);
            success.setStatus(1);
            success.setCancelReason(null);
            orderMapper.updateById(success);
            log.info("秒杀订单状态更新成功: orderId={}, status=SUCCESS", orderId);
        } catch (Exception e) {
            log.error("秒杀订单消费异常: message={}", message, e);
            throw new RuntimeException("秒杀订单消费异常", e);
        }
    }
}
