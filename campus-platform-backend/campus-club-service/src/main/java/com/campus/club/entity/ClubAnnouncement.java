package com.campus.club.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("club_announcement")
public class ClubAnnouncement extends BaseEntity {
    private Long clubId;
    private String title;
    private String content;
    private Long publisherId;
    private Integer isPinned;
}
