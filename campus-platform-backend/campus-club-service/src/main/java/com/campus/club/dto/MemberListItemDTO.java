package com.campus.club.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社团成员列表条目 DTO，用于 GET /clubs/{clubId}/members 响应。
 */
@Data
public class MemberListItemDTO {

    /** club_member.id */
    private Long memberId;

    private Long userId;

    /** 0-普通成员 1-副社长 2-社长 */
    private Integer memberRole;

    private LocalDateTime joinTime;
}
