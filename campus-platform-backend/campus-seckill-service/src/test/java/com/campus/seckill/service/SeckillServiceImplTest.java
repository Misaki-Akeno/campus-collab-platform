package com.campus.seckill.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BizException;
import com.campus.common.exception.ErrorCode;
import com.campus.seckill.entity.SeckillActivity;
import com.campus.seckill.entity.SeckillOrder;
import com.campus.seckill.mapper.SeckillActivityMapper;
import com.campus.seckill.mapper.SeckillOrderMapper;
import com.campus.seckill.service.impl.SeckillServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeckillServiceImplTest {

    @Mock private SeckillActivityMapper activityMapper;
    @Mock private SeckillOrderMapper orderMapper;

    @InjectMocks
    private SeckillServiceImpl seckillService;

    private long nextId = 1000L;

    @BeforeEach
    void setUp() {
        // lenient: these stubs are only exercised in book() success paths
        lenient().doAnswer(inv -> {
            SeckillOrder order = inv.getArgument(0);
            order.setId(nextId++);
            return 1;
        }).when(orderMapper).insert(any(SeckillOrder.class));

        lenient().when(orderMapper.selectCount(any())).thenReturn(0L);
        lenient().when(activityMapper.deductAvailableStock(anyLong())).thenReturn(1);
    }

    // ===================== listActivities =====================

    @Test
    void listActivities_returnPagedResult() {
        Page<SeckillActivity> page = new Page<>(1, 10);
        SeckillActivity act = buildActivity(1L, 1, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(5));
        page.setRecords(List.of(act));
        page.setTotal(1L);

        when(activityMapper.selectPage(any(Page.class), any())).thenReturn(page);

        Page<SeckillActivity> result = seckillService.listActivities(1, 10, null, null);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        assertEquals(1L, result.getTotal());
        verify(activityMapper).selectPage(any(Page.class), any());
    }

    @Test
    void listActivities_withClubIdAndStatusFilter() {
        Page<SeckillActivity> page = new Page<>(1, 5);
        page.setRecords(List.of());
        page.setTotal(0L);

        when(activityMapper.selectPage(any(Page.class), any())).thenReturn(page);

        Page<SeckillActivity> result = seckillService.listActivities(1, 5, 42L, 1);

        assertNotNull(result);
        assertEquals(0, result.getRecords().size());
        verify(activityMapper).selectPage(any(Page.class), any());
    }

    // ===================== getActivity =====================

    @Test
    void getActivity_success() {
        SeckillActivity act = buildActivity(1L, 1, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(5));
        when(activityMapper.selectById(1L)).thenReturn(act);

        SeckillActivity result = seckillService.getActivity(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void getActivity_notFound_throwsBizException() {
        when(activityMapper.selectById(99L)).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> seckillService.getActivity(99L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }

    // ===================== book =====================

    @Test
    void book_success_databaseDeductionAndOrderAreBothWritten() {
        SeckillActivity act = buildActivity(1L, 1, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(5));
        when(activityMapper.selectById(1L)).thenReturn(act);

        String orderId = seckillService.book(1L, 100L);

        assertNotNull(orderId);
        // orderMapper.insert must be called exactly once
        verify(orderMapper).insert(any(SeckillOrder.class));
        verify(activityMapper).deductAvailableStock(1L);
    }

    @Test
    void book_activityNotFound_throwsBizException() {
        when(activityMapper.selectById(1L)).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> seckillService.book(1L, 100L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    void book_activityStatusNotActive_throwsBizException() {
        // status = 2 (已结束) — any status != 1 should throw ACTIVITY_CANCELLED
        SeckillActivity act = buildActivity(1L, 2, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(5));
        when(activityMapper.selectById(1L)).thenReturn(act);

        BizException e = assertThrows(BizException.class, () -> seckillService.book(1L, 100L));
        assertEquals(ErrorCode.ACTIVITY_CANCELLED.getCode(), e.getCode());
    }

    @Test
    void book_activityNotStarted_throwsBizException() {
        // startTime = now + 5h — activity hasn't started yet
        SeckillActivity act = buildActivity(1L, 1, LocalDateTime.now().plusHours(5), LocalDateTime.now().plusHours(10));
        when(activityMapper.selectById(1L)).thenReturn(act);

        BizException e = assertThrows(BizException.class, () -> seckillService.book(1L, 100L));
        assertEquals(ErrorCode.ACTIVITY_NOT_START.getCode(), e.getCode());
    }

    @Test
    void book_activityExpired_throwsBizException() {
        // endTime = now - 5h — activity is over
        SeckillActivity act = buildActivity(1L, 1, LocalDateTime.now().minusHours(10), LocalDateTime.now().minusHours(5));
        when(activityMapper.selectById(1L)).thenReturn(act);

        BizException e = assertThrows(BizException.class, () -> seckillService.book(1L, 100L));
        assertEquals(ErrorCode.ACTIVITY_ENDED.getCode(), e.getCode());
    }

    @Test
    void book_stockEmpty_databaseConditionalUpdateReturnsZero() {
        SeckillActivity act = buildActivity(1L, 1, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(5));
        when(activityMapper.selectById(1L)).thenReturn(act);

        when(activityMapper.deductAvailableStock(1L)).thenReturn(0);

        BizException e = assertThrows(BizException.class, () -> seckillService.book(1L, 100L));
        assertEquals(ErrorCode.STOCK_EMPTY.getCode(), e.getCode());
        verify(orderMapper, never()).insert(any(SeckillOrder.class));
    }

    @Test
    void book_duplicateBook_existingOrder_throwsBizException() {
        SeckillActivity act = buildActivity(1L, 1, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(5));
        when(activityMapper.selectById(1L)).thenReturn(act);

        when(orderMapper.selectCount(any())).thenReturn(1L);

        BizException e = assertThrows(BizException.class, () -> seckillService.book(1L, 100L));
        assertEquals(ErrorCode.DUPLICATE_BOOK.getCode(), e.getCode());
        verify(activityMapper, never()).deductAvailableStock(anyLong());
        verify(orderMapper, never()).insert(any(SeckillOrder.class));
    }

    @Test
    void book_concurrentDuplicate_rollsBackViaBusinessException() {
        SeckillActivity act = buildActivity(1L, 1, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(5));
        when(activityMapper.selectById(1L)).thenReturn(act);
        doThrow(new DuplicateKeyException("uk_user_activity"))
                .when(orderMapper).insert(any(SeckillOrder.class));

        BizException error = assertThrows(BizException.class,
                () -> seckillService.book(1L, 100L));

        assertEquals(ErrorCode.DUPLICATE_BOOK.getCode(), error.getCode());
        verify(activityMapper).deductAvailableStock(1L);
    }

    // ===================== getOrder =====================

    @Test
    void getOrder_success() {
        SeckillOrder order = new SeckillOrder();
        order.setId(1000L);
        order.setUserId(100L);
        order.setActivityId(1L);
        order.setStatus(1);
        when(orderMapper.selectById(1000L)).thenReturn(order);

        SeckillOrder result = seckillService.getOrder(1000L);

        assertNotNull(result);
        assertEquals(1000L, result.getId());
        assertEquals(100L, result.getUserId());
    }

    @Test
    void getOrder_notFound_throwsBizException() {
        when(orderMapper.selectById(9999L)).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> seckillService.getOrder(9999L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }

    // ===================== helpers =====================

    private SeckillActivity buildActivity(Long id, int status, LocalDateTime startTime, LocalDateTime endTime) {
        SeckillActivity act = new SeckillActivity();
        act.setId(id);
        act.setClubId(10L);
        act.setTitle("Test Activity");
        act.setTotalStock(100);
        act.setAvailableStock(50);
        act.setStatus(status);
        act.setStartTime(startTime);
        act.setEndTime(endTime);
        return act;
    }
}
