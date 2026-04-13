package com.campus.club.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("club")
public class Club extends BaseEntity {
    private String name;
    private String description;
    private String logoUrl;
    private Long leaderId;
    private String category;
    /** 0-待审核 1-正常 2-已解散 */
    private Integer status;
    private Integer memberCount;
}
