package com.campus.api.club;

import lombok.Data;

/**
 * 用户社团关系 DTO，供 ClubFeignClient.getUserClubs() 使用。
 * 对应 API.md A4 中 clubs 数组的单元素结构：{clubId, clubName, memberRole}
 */
@Data
public class ClubMemberDTO {

    private Long clubId;

    private String clubName;

    /** 社团内角色：0-普通成员 1-副社长 2-社长 */
    private Integer memberRole;
}
