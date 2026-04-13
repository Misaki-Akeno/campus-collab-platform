package com.campus.club.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("club_member")
public class ClubMember extends BaseEntity {
    private Long userId;
    private Long clubId;
    /** 0-成员 1-副社长 2-社长 */
    private Integer memberRole;
    private LocalDateTime joinTime;
}
