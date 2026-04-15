package com.campus.seckill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("seckill_order")
public class SeckillOrder extends BaseEntity {
    private Long userId;
    private Long activityId;
    /** 0-处理中(PROCESSING) 1-成功(SUCCESS) 2-失败/已取消(FAILED/CANCELLED) */
    private Integer status;
    private String cancelReason;
}
