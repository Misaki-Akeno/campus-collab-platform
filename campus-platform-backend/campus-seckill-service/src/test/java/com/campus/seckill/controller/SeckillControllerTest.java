package com.campus.seckill.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BizException;
import com.campus.common.exception.ErrorCode;
import com.campus.common.exception.GlobalExceptionHandler;
import com.campus.seckill.entity.SeckillActivity;
import com.campus.seckill.entity.SeckillOrder;
import com.campus.seckill.service.SeckillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SeckillControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SeckillService seckillService;

    @InjectMocks
    private SeckillController seckillController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(seckillController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ===================== GET /api/v1/activities =====================

    @Test
    void listActivities_success() throws Exception {
        Page<SeckillActivity> page = new Page<>(1, 20);
        SeckillActivity activity = buildActivity(1L, 10L, 1);
        page.setRecords(List.of(activity));
        page.setTotal(1L);

        when(seckillService.listActivities(1, 20, null, null)).thenReturn(page);

        mockMvc.perform(get("/api/v1/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].id").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void listActivities_withFilter() throws Exception {
        Page<SeckillActivity> page = new Page<>(1, 20);
        SeckillActivity activity = buildActivity(2L, 1L, 1);
        page.setRecords(List.of(activity));
        page.setTotal(1L);

        when(seckillService.listActivities(1, 20, 1L, 1)).thenReturn(page);

        mockMvc.perform(get("/api/v1/activities")
                        .param("clubId", "1")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].clubId").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    // ===================== GET /api/v1/activities/{activityId} =====================

    @Test
    void getActivity_success() throws Exception {
        SeckillActivity activity = buildActivity(1L, 10L, 1);
        when(seckillService.getActivity(1L)).thenReturn(activity);

        mockMvc.perform(get("/api/v1/activities/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("Test Activity"))
                .andExpect(jsonPath("$.data.status").value(1));
    }

    @Test
    void getActivity_notFound() throws Exception {
        when(seckillService.getActivity(99L))
                .thenThrow(new BizException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/v1/activities/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ===================== POST /api/v1/activities/{activityId}/book =====================

    @Test
    void book_success() throws Exception {
        when(seckillService.book(1L, 1L)).thenReturn("order-abc-123");

        mockMvc.perform(post("/api/v1/activities/1/book")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.orderId").value("order-abc-123"));
    }

    @Test
    void book_missingUserId_returnsSystemError() throws Exception {
        // GlobalExceptionHandler.handleException catches MissingRequestHeaderException
        // and returns HTTP 200 with SYSTEM_ERROR code (500).
        mockMvc.perform(post("/api/v1/activities/1/book"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR.getCode()));
    }

    @Test
    void book_stockEmpty() throws Exception {
        when(seckillService.book(1L, 1L))
                .thenThrow(new BizException(ErrorCode.STOCK_EMPTY));

        mockMvc.perform(post("/api/v1/activities/1/book")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.STOCK_EMPTY.getCode()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ===================== GET /api/v1/orders/{orderId} =====================

    @Test
    void getOrder_success() throws Exception {
        SeckillOrder order = buildOrder(100L, 1L, 1L, 1);
        when(seckillService.getOrder(100L)).thenReturn(order);

        mockMvc.perform(get("/api/v1/orders/100")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.activityId").value(1))
                .andExpect(jsonPath("$.data.status").value(1));
    }

    @Test
    void getOrder_notFound() throws Exception {
        when(seckillService.getOrder(9999L))
                .thenThrow(new BizException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/v1/orders/9999")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void getOrder_forbiddenForOtherUser() throws Exception {
        // Order belongs to userId=2, requesting as userId=1 — controller throws FORBIDDEN
        SeckillOrder order = buildOrder(100L, 2L, 1L, 1);
        when(seckillService.getOrder(100L)).thenReturn(order);

        mockMvc.perform(get("/api/v1/orders/100")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void getOrder_missingUserId_returnsSystemError() throws Exception {
        // Missing X-User-Id header — caught by GlobalExceptionHandler catch-all, returns SYSTEM_ERROR
        mockMvc.perform(get("/api/v1/orders/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR.getCode()));
    }

    // ===================== helpers =====================

    private SeckillActivity buildActivity(Long id, Long clubId, int status) {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(id);
        activity.setClubId(clubId);
        activity.setTitle("Test Activity");
        activity.setTotalStock(100);
        activity.setAvailableStock(50);
        activity.setStatus(status);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(5));
        return activity;
    }

    private SeckillOrder buildOrder(Long id, Long userId, Long activityId, int status) {
        SeckillOrder order = new SeckillOrder();
        order.setId(id);
        order.setUserId(userId);
        order.setActivityId(activityId);
        order.setStatus(status);
        return order;
    }
}
