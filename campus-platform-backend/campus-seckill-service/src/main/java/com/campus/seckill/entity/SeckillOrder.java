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
    /** 0-排队中 1-成功 2-已取消 */
    private Integer status;
    private String cancelReason;
}
