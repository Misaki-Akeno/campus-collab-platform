package com.campus.seckill.consumer;

import com.campus.seckill.entity.SeckillOrder;
import com.campus.seckill.mapper.SeckillOrderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeckillOrderConsumerTest {

    @Mock private SeckillOrderMapper orderMapper;

    private SeckillOrderConsumer consumer;

    @BeforeEach
    void setUp() {
        // Use constructor injection directly — avoids fragile reflection-based field override.
        consumer = new SeckillOrderConsumer(orderMapper, new ObjectMapper());
    }

    // ===================== onMessage =====================

    @Test
    void onMessage_processingOrder_updatesToSuccess() {
        // status = 0 (PROCESSING) → should be updated to SUCCESS(1)
        SeckillOrder order = new SeckillOrder();
        order.setId(101L);
        order.setUserId(200L);
        order.setActivityId(1L);
        order.setStatus(0);

        when(orderMapper.selectById(101L)).thenReturn(order);
        when(orderMapper.updateById(any(SeckillOrder.class))).thenReturn(1);

        String message = "{\"orderId\":101,\"userId\":200,\"activityId\":1,\"remainStock\":4}";
        consumer.onMessage(message);

        verify(orderMapper, times(1)).updateById(argThat((SeckillOrder o) -> o.getId().equals(101L) && o.getStatus() == 1));
    }

    @Test
    void onMessage_alreadySuccessOrder_skips() {
        // status = 1 (SUCCESS) → already terminal, updateById must NOT be called
        SeckillOrder order = new SeckillOrder();
        order.setId(102L);
        order.setStatus(1);

        when(orderMapper.selectById(102L)).thenReturn(order);

        consumer.onMessage("{\"orderId\":102,\"userId\":200,\"activityId\":1}");

        verify(orderMapper, never()).updateById(any(SeckillOrder.class));
    }

    @Test
    void onMessage_alreadyFailedOrder_skips() {
        // status = 2 (FAILED/CANCELLED) → already terminal, updateById must NOT be called
        SeckillOrder order = new SeckillOrder();
        order.setId(103L);
        order.setStatus(2);

        when(orderMapper.selectById(103L)).thenReturn(order);

        consumer.onMessage("{\"orderId\":103,\"userId\":200,\"activityId\":1}");

        verify(orderMapper, never()).updateById(any(SeckillOrder.class));
    }

    @Test
    void onMessage_orderNotFound_skips() {
        when(orderMapper.selectById(999L)).thenReturn(null);

        assertDoesNotThrow(() ->
                consumer.onMessage("{\"orderId\":999,\"userId\":200,\"activityId\":1}"));
        verify(orderMapper, never()).updateById(any(SeckillOrder.class));
    }

    @Test
    void onMessage_nullOrderIdInJson_skipsWithNoUpdate() {
        // {"orderId": null} — before fix asLong() returned 0L, bypassing the null guard;
        // after fix isNull() check prevents DB lookup with id=0.
        assertDoesNotThrow(() ->
                consumer.onMessage("{\"orderId\":null,\"userId\":200,\"activityId\":1}"));
        verify(orderMapper, never()).selectById(any());
        verify(orderMapper, never()).updateById(any(SeckillOrder.class));
    }

    @Test
    void onMessage_missingOrderIdField_skipsWithNoUpdate() {
        assertDoesNotThrow(() ->
                consumer.onMessage("{\"userId\":200,\"activityId\":1}"));
        verify(orderMapper, never()).selectById(any());
        verify(orderMapper, never()).updateById(any(SeckillOrder.class));
    }
}

