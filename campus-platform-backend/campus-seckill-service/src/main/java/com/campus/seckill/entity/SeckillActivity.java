package com.campus.seckill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("seckill_activity")
public class SeckillActivity extends BaseEntity {
    private Long clubId;
    private String title;
    private String description;
    private String coverUrl;
    private String location;
    private LocalDateTime activityTime;
    private Integer totalStock;
    private Integer availableStock;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 0-未开始 1-进行中 2-已结束 3-已取消 */
    private Integer status;
}
